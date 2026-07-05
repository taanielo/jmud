package io.taanielo.jmud.core.combat;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessageContext;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageRenderer;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Resolves deterministic combat results for a single action.
 *
 * <p>Random numbers are sourced through a {@link CombatRandomProvider} so that
 * each encounter can be seeded independently from the world seed, the current
 * tick, and the attacker's identifier. The derived seed is captured in
 * {@link CombatResult#rngSeed()} and written to the combat audit trail, making
 * any fight reproducible from the audit log.
 */
public class CombatEngine {
    private final AttackRepository attackRepository;
    private final CombatModifierResolver modifierResolver;
    private final RaceArmorBonusResolver armorBonusResolver;
    private final EquipmentArmorResolver equipmentArmorResolver;
    private final CombatRandomProvider randomProvider;
    private final LongSupplier tickSupplier;
    private final MessageRenderer renderer = new MessageRenderer();

    // ── Legacy constructors (wrap a fixed CombatRandom; tick is always 0) ─────────

    /**
     * Creates a combat engine with the provided dependencies.
     * The supplied {@link CombatRandom} is used for every encounter (tick-independent).
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        CombatRandom random
    ) {
        this(attackRepository, modifierResolver, RaceArmorBonusResolver.noOp(), EquipmentArmorResolver.noOp(), random);
    }

    /**
     * Creates a combat engine with race armor bonus resolution.
     * The supplied {@link CombatRandom} is used for every encounter (tick-independent).
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        CombatRandom random
    ) {
        this(attackRepository, modifierResolver, armorBonusResolver, EquipmentArmorResolver.noOp(), random);
    }

    /**
     * Creates a combat engine with both race and equipment armor resolution.
     * The supplied {@link CombatRandom} is used for every encounter (tick-independent).
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        CombatRandom random
    ) {
        this(
            attackRepository,
            modifierResolver,
            armorBonusResolver,
            equipmentArmorResolver,
            (tick, actorId) -> random,
            () -> 0L
        );
    }

    // ── Seeded constructors (CombatRandomProvider + tick source) ─────────────────

    /**
     * Creates a combat engine that derives a per-encounter seed from the tick and
     * actor, using the provided {@link CombatRandomProvider} and tick source.
     *
     * @param attackRepository       source of attack definitions
     * @param modifierResolver       resolves combat modifier chains from player effects
     * @param armorBonusResolver     resolves race-based AC bonuses on the target
     * @param equipmentArmorResolver resolves equipment-based AC bonuses on the target
     * @param randomProvider         produces a fresh {@link CombatRandom} per encounter
     * @param tickSupplier           supplies the current world tick number
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        CombatRandomProvider randomProvider,
        LongSupplier tickSupplier
    ) {
        this.attackRepository = Objects.requireNonNull(attackRepository, "Attack repository is required");
        this.modifierResolver = Objects.requireNonNull(modifierResolver, "Modifier resolver is required");
        this.armorBonusResolver = Objects.requireNonNull(armorBonusResolver, "Armor bonus resolver is required");
        this.equipmentArmorResolver = Objects.requireNonNull(equipmentArmorResolver, "Equipment armor resolver is required");
        this.randomProvider = Objects.requireNonNull(randomProvider, "Combat random provider is required");
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "Tick supplier is required");
    }

    /**
     * Resolves a single combat action between the attacker and target.
     *
     * @param attacker  the attacking player
     * @param target    the defending player
     * @param attackId  the attack to use
     * @return the combat result, including the RNG seed used
     */
    public CombatResult resolve(Player attacker, Player target, AttackId attackId)
        throws RepositoryException, EffectRepositoryException {
        return resolve(new CombatAction(attacker, target, attackId));
    }

    /**
     * Resolves a single combat action.
     *
     * @param action the combat action describing attacker, target, and attack
     * @return the combat result, including the RNG seed used
     */
    public CombatResult resolve(CombatAction action)
        throws RepositoryException, EffectRepositoryException {
        Objects.requireNonNull(action, "Combat action is required");
        Player attacker = action.attacker();
        Player target = action.target();
        AttackId attackId = action.attackId();

        long tick = tickSupplier.getAsLong();
        String actorId = attacker.getUsername().getValue();
        CombatRandom random = randomProvider.forEncounter(tick, actorId);
        long rngSeed = (random instanceof SeededCombatRandom scr) ? scr.seed() : 0L;

        AttackDefinition attack = attackRepository.findById(attackId)
            .orElseThrow(() -> new RepositoryException("Unknown attack id " + attackId.getValue()));
        CombatModifiers attackerMods = modifierResolver.resolve(attacker.effects());
        CombatModifiers targetMods = modifierResolver.resolve(target.effects());
        int targetArmorBonus = armorBonusResolver.armorBonus(target) + equipmentArmorResolver.totalAc(target);

        int hitChanceBase = CombatSettings.baseHitChance()
            + attack.hitBonus()
            + attackerMods.attack().apply(0)
            - targetMods.defense().apply(0)
            - targetArmorBonus;
        int hitChance = attackerMods.hitChance().apply(hitChanceBase);
        hitChance = clamp(hitChance, 0, 100);
        int hitRoll = random.roll(1, 100);
        boolean hit = hitRoll <= hitChance;

        int damage = 0;
        boolean crit = false;
        if (hit) {
            int baseDamage = random.roll(attack.minDamage(), attack.maxDamage());
            int adjusted = attackerMods.damage().apply(baseDamage + attack.damageBonus());
            int variance = CombatSettings.damageVariancePercent();
            if (variance > 0) {
                int varianceRoll = random.roll(-variance, variance);
                adjusted = Math.max(0, (int) Math.round(adjusted * (1 + (varianceRoll / 100.0))));
            }
            int critChance = CombatSettings.baseCritChance()
                + attack.critBonus();
            critChance = attackerMods.critChance().apply(critChance);
            critChance = clamp(critChance, 0, 100);
            int critRoll = random.roll(1, 100);
            crit = critRoll <= critChance;
            if (crit) {
                adjusted *= CombatSettings.critMultiplier();
            }
            damage = Math.max(0, adjusted);
        }

        Player updatedTarget = hit && damage > 0
            ? target.withVitals(target.getVitals().damage(damage))
            : target;

        String sourceMessage = null;
        String targetMessage = null;
        String roomMessage = null;
        String targetName = target.getUsername().getValue();
        String attackerName = attacker.getUsername().getValue();
        List<MessageSpec> specs = attack.messages();
        if (!specs.isEmpty()) {
            MessagePhase phase = !hit
                ? MessagePhase.ATTACK_MISS
                : (crit ? MessagePhase.ATTACK_CRIT : MessagePhase.ATTACK_HIT);
            MessageContext context = new MessageContext(
                attacker.getUsername(),
                target.getUsername(),
                attackerName,
                targetName,
                null,
                null,
                attack.name(),
                damage
            );
            for (MessageSpec spec : specs) {
                if (spec.phase() != phase) {
                    continue;
                }
                String rendered = renderer.render(spec, context);
                if (rendered == null || rendered.isBlank()) {
                    continue;
                }
                if (spec.channel() == MessageChannel.SELF) {
                    sourceMessage = rendered;
                } else if (spec.channel() == MessageChannel.TARGET) {
                    targetMessage = rendered;
                } else if (spec.channel() == MessageChannel.ROOM) {
                    roomMessage = rendered;
                }
            }
        } else if (!hit) {
            sourceMessage = "You miss " + targetName + ".";
            targetMessage = attackerName + " misses you.";
            roomMessage = attackerName + " misses " + targetName + ".";
        } else if (crit) {
            sourceMessage = "You critically hit " + targetName + " for " + damage + ".";
            targetMessage = attackerName + " critically hits you for " + damage + ".";
            roomMessage = attackerName + " critically hits " + targetName + ".";
        } else {
            sourceMessage = "You hit " + targetName + " for " + damage + ".";
            targetMessage = attackerName + " hits you for " + damage + ".";
            roomMessage = attackerName + " hits " + targetName + ".";
        }

        return new CombatResult(attacker, updatedTarget, hit, crit, damage,
            sourceMessage, targetMessage, roomMessage, rngSeed);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
