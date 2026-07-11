package io.taanielo.jmud.core.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectMessageSink;
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
    private final RaceAttackBonusResolver attackBonusResolver;
    private final ClassArmorBonusResolver classArmorBonusResolver;
    private final EquipmentArmorResolver equipmentArmorResolver;
    private final ShieldBlockResolver shieldBlockResolver;
    private final OffhandAttackResolver offhandAttackResolver;
    private final CombatRandomProvider randomProvider;
    private final LongSupplier tickSupplier;
    private final EffectEngine effectEngine;
    private final MessageRenderer renderer = new MessageRenderer();

    // ── Legacy constructors (wrap a fixed CombatRandom; tick is always 0) ─────────

    /**
     * Creates a combat engine with the provided dependencies.
     * The supplied {@link CombatRandom} is used for every encounter (tick-independent).
     * On-hit status effect application is disabled (no {@link EffectEngine} supplied).
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        CombatRandom random
    ) {
        this(attackRepository, modifierResolver, RaceArmorBonusResolver.noOp(), EquipmentArmorResolver.noOp(), random, null);
    }

    /**
     * Creates a combat engine with the provided dependencies and an {@link EffectEngine}
     * capable of applying on-hit status effects (see {@link AttackDefinition#effectOnHit()}).
     * The supplied {@link CombatRandom} is used for every encounter (tick-independent).
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        CombatRandom random,
        EffectEngine effectEngine
    ) {
        this(attackRepository, modifierResolver, RaceArmorBonusResolver.noOp(), EquipmentArmorResolver.noOp(), random, effectEngine);
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
        this(attackRepository, modifierResolver, armorBonusResolver, EquipmentArmorResolver.noOp(), random, null);
    }

    /**
     * Creates a combat engine with both race and equipment armor resolution.
     * The supplied {@link CombatRandom} is used for every encounter (tick-independent).
     * On-hit status effect application is disabled (no {@link EffectEngine} supplied).
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        CombatRandom random
    ) {
        this(attackRepository, modifierResolver, armorBonusResolver, equipmentArmorResolver, random, null);
    }

    /**
     * Creates a combat engine with both race and equipment armor resolution and an
     * {@link EffectEngine} capable of applying on-hit status effects.
     * The supplied {@link CombatRandom} is used for every encounter (tick-independent).
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        CombatRandom random,
        EffectEngine effectEngine
    ) {
        this(
            attackRepository,
            modifierResolver,
            armorBonusResolver,
            equipmentArmorResolver,
            (tick, actorId) -> random,
            () -> 0L,
            effectEngine
        );
    }

    // ── Seeded constructors (CombatRandomProvider + tick source) ─────────────────

    /**
     * Creates a combat engine that derives a per-encounter seed from the tick and
     * actor, using the provided {@link CombatRandomProvider} and tick source.
     * On-hit status effect application is disabled (no {@link EffectEngine} supplied).
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
        this(attackRepository, modifierResolver, armorBonusResolver, equipmentArmorResolver, randomProvider, tickSupplier, null);
    }

    /**
     * Creates a combat engine that derives a per-encounter seed from the tick and
     * actor, using the provided {@link CombatRandomProvider} and tick source, and
     * capable of applying on-hit status effects via the supplied {@link EffectEngine}.
     *
     * @param attackRepository       source of attack definitions
     * @param modifierResolver       resolves combat modifier chains from player effects
     * @param armorBonusResolver     resolves race-based AC bonuses on the target
     * @param equipmentArmorResolver resolves equipment-based AC bonuses on the target
     * @param randomProvider         produces a fresh {@link CombatRandom} per encounter
     * @param tickSupplier           supplies the current world tick number
     * @param effectEngine           applies {@link AttackDefinition#effectOnHit()} to targets;
     *                               {@code null} disables on-hit effect application
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        CombatRandomProvider randomProvider,
        LongSupplier tickSupplier,
        EffectEngine effectEngine
    ) {
        this(
            attackRepository,
            modifierResolver,
            armorBonusResolver,
            RaceAttackBonusResolver.noOp(),
            equipmentArmorResolver,
            randomProvider,
            tickSupplier,
            effectEngine
        );
    }

    /**
     * Creates a combat engine that resolves a race-based attack modifier for the attacker
     * but no class-based armour bonus (defaults to {@link ClassArmorBonusResolver#noOp()}).
     *
     * @param attackRepository       source of attack definitions
     * @param modifierResolver       resolves combat modifier chains from player effects
     * @param armorBonusResolver     resolves race-based AC bonuses on the target
     * @param attackBonusResolver    resolves race-based attack (hit-chance) bonuses on the attacker
     * @param equipmentArmorResolver resolves equipment-based AC bonuses on the target
     * @param randomProvider         produces a fresh {@link CombatRandom} per encounter
     * @param tickSupplier           supplies the current world tick number
     * @param effectEngine           applies {@link AttackDefinition#effectOnHit()} to targets;
     *                               {@code null} disables on-hit effect application
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        RaceAttackBonusResolver attackBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        CombatRandomProvider randomProvider,
        LongSupplier tickSupplier,
        EffectEngine effectEngine
    ) {
        this(
            attackRepository,
            modifierResolver,
            armorBonusResolver,
            attackBonusResolver,
            ClassArmorBonusResolver.noOp(),
            equipmentArmorResolver,
            ShieldBlockResolver.noOp(),
            OffhandAttackResolver.disabled(),
            randomProvider,
            tickSupplier,
            effectEngine
        );
    }

    /**
     * Creates a combat engine that resolves a class-based armour bonus and shield block but no
     * dual-wield off-hand attack (defaults to {@link OffhandAttackResolver#disabled()}).
     *
     * @param attackRepository        source of attack definitions
     * @param modifierResolver        resolves combat modifier chains from player effects
     * @param armorBonusResolver      resolves race-based AC bonuses on the target
     * @param attackBonusResolver     resolves race-based attack (hit-chance) bonuses on the attacker
     * @param classArmorBonusResolver resolves class-based AC bonuses on the target
     * @param equipmentArmorResolver  resolves equipment-based AC bonuses on the target
     * @param shieldBlockResolver     resolves the target's off-hand shield block chance/reduction
     * @param randomProvider          produces a fresh {@link CombatRandom} per encounter
     * @param tickSupplier            supplies the current world tick number
     * @param effectEngine            applies {@link AttackDefinition#effectOnHit()} to targets;
     *                                {@code null} disables on-hit effect application
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        RaceAttackBonusResolver attackBonusResolver,
        ClassArmorBonusResolver classArmorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        ShieldBlockResolver shieldBlockResolver,
        CombatRandomProvider randomProvider,
        LongSupplier tickSupplier,
        EffectEngine effectEngine
    ) {
        this(
            attackRepository,
            modifierResolver,
            armorBonusResolver,
            attackBonusResolver,
            classArmorBonusResolver,
            equipmentArmorResolver,
            shieldBlockResolver,
            OffhandAttackResolver.disabled(),
            randomProvider,
            tickSupplier,
            effectEngine
        );
    }

    /**
     * Creates a combat engine that additionally resolves a dual-wield off-hand attack for the
     * attacker. This is the fully-wired constructor used by the composition root.
     *
     * @param attackRepository        source of attack definitions
     * @param modifierResolver        resolves combat modifier chains from player effects
     * @param armorBonusResolver      resolves race-based AC bonuses on the target
     * @param attackBonusResolver     resolves race-based attack (hit-chance) bonuses on the attacker
     * @param classArmorBonusResolver resolves class-based AC bonuses on the target
     * @param equipmentArmorResolver  resolves equipment-based AC bonuses on the target
     * @param shieldBlockResolver     resolves the target's off-hand shield block chance/reduction
     * @param offhandAttackResolver   resolves the attacker's off-hand dual-wield weapon, if any
     * @param randomProvider          produces a fresh {@link CombatRandom} per encounter
     * @param tickSupplier            supplies the current world tick number
     * @param effectEngine            applies {@link AttackDefinition#effectOnHit()} to targets;
     *                                {@code null} disables on-hit effect application
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        RaceArmorBonusResolver armorBonusResolver,
        RaceAttackBonusResolver attackBonusResolver,
        ClassArmorBonusResolver classArmorBonusResolver,
        EquipmentArmorResolver equipmentArmorResolver,
        ShieldBlockResolver shieldBlockResolver,
        OffhandAttackResolver offhandAttackResolver,
        CombatRandomProvider randomProvider,
        LongSupplier tickSupplier,
        EffectEngine effectEngine
    ) {
        this.attackRepository = Objects.requireNonNull(attackRepository, "Attack repository is required");
        this.modifierResolver = Objects.requireNonNull(modifierResolver, "Modifier resolver is required");
        this.armorBonusResolver = Objects.requireNonNull(armorBonusResolver, "Armor bonus resolver is required");
        this.attackBonusResolver = Objects.requireNonNull(attackBonusResolver, "Attack bonus resolver is required");
        this.classArmorBonusResolver =
            Objects.requireNonNull(classArmorBonusResolver, "Class armor bonus resolver is required");
        this.equipmentArmorResolver = Objects.requireNonNull(equipmentArmorResolver, "Equipment armor resolver is required");
        this.shieldBlockResolver = Objects.requireNonNull(shieldBlockResolver, "Shield block resolver is required");
        this.offhandAttackResolver =
            Objects.requireNonNull(offhandAttackResolver, "Offhand attack resolver is required");
        this.randomProvider = Objects.requireNonNull(randomProvider, "Combat random provider is required");
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "Tick supplier is required");
        this.effectEngine = effectEngine;
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
        int targetArmorBonus = armorBonusResolver.armorBonus(target)
            + classArmorBonusResolver.armorBonus(target)
            + equipmentArmorResolver.totalAc(target);
        int attackerRaceAttackBonus = attackBonusResolver.attackBonus(attacker);

        // Main-hand attack: resolves exactly as before. The off-hand roll (below) only runs when the
        // attacker dual-wields a weapon, so equipment without an off-hand weapon leaves the RNG
        // stream — and therefore every existing result — unchanged.
        AttackOutcome main = resolveSingleAttack(
            attacker, target, attack, random, attackerMods, targetMods,
            targetArmorBonus, attackerRaceAttackBonus, action, null);

        Player currentTarget = main.updatedTarget();

        CombatResult.OffhandResult offhandResult = null;
        Optional<OffhandAttackResolver.OffhandWeapon> offhandWeapon = offhandAttackResolver.resolve(attacker);
        if (offhandWeapon.isPresent()) {
            AttackDefinition offhandAttack =
                attackRepository.findById(offhandWeapon.get().attackId()).orElse(null);
            if (offhandAttack != null) {
                AttackOutcome off = resolveSingleAttack(
                    attacker, currentTarget, offhandAttack, random, attackerMods, targetMods,
                    targetArmorBonus, attackerRaceAttackBonus, action,
                    offhandWeapon.get().weaponName());
                currentTarget = off.updatedTarget();
                offhandResult = new CombatResult.OffhandResult(
                    off.hit(), off.crit(), off.blocked(), off.damage(),
                    off.sourceMessage(), off.targetMessage(), off.roomMessage(),
                    off.effectTargetMessages(), off.effectRoomMessages());
            }
        }

        return new CombatResult(attacker, currentTarget, main.hit(), main.crit(), main.blocked(),
            main.damage(), main.sourceMessage(), main.targetMessage(), main.roomMessage(), rngSeed,
            main.effectTargetMessages(), main.effectRoomMessages(), offhandResult);
    }

    /**
     * Resolves one attack (hit/damage/block/crit/effects/messages) against a target. When
     * {@code offhandWeaponName} is non-null the attack is a dual-wield off-hand swing: it lands at a
     * reduced hit chance ({@link CombatSettings#offhandHitPenaltyPercent()}), deals reduced damage
     * ({@link CombatSettings#offhandDamagePercent()}), and produces off-hand-specific messages that
     * name the weapon so both round hits are distinguishable.
     */
    private AttackOutcome resolveSingleAttack(
        Player attacker,
        Player target,
        AttackDefinition attack,
        CombatRandom random,
        CombatModifiers attackerMods,
        CombatModifiers targetMods,
        int targetArmorBonus,
        int attackerRaceAttackBonus,
        CombatAction action,
        String offhandWeaponName
    ) throws EffectRepositoryException {
        boolean offhand = offhandWeaponName != null;
        int hitChanceBase = CombatSettings.baseHitChance()
            + attack.hitBonus()
            + attackerMods.attack().apply(0)
            + attackerRaceAttackBonus
            - targetMods.defense().apply(0)
            - targetArmorBonus;
        int hitChance = attackerMods.hitChance().apply(hitChanceBase);
        hitChance += action.environmentHitModifier();
        if (attack.isRanged()) {
            hitChance += action.environmentRangedHitModifier();
        }
        if (offhand) {
            hitChance -= CombatSettings.offhandHitPenaltyPercent();
        }
        hitChance = clamp(hitChance, 0, 100);
        int hitRoll = random.roll(1, 100);
        boolean hit = hitRoll <= hitChance;

        int damage = 0;
        boolean crit = false;
        boolean blocked = false;
        if (hit) {
            int baseDamage = random.roll(attack.minDamage(), attack.maxDamage());
            int adjusted = attackerMods.damage().apply(baseDamage + attack.damageBonus());
            int variance = CombatSettings.damageVariancePercent();
            if (variance > 0) {
                int varianceRoll = random.roll(-variance, variance);
                adjusted = Math.max(0, (int) Math.round(adjusted * (1 + (varianceRoll / 100.0))));
            }
            // A shield in the target's off-hand may block an otherwise-landing hit. Only roll when a
            // block is possible so equipment without a shield leaves the RNG stream (and therefore
            // every existing result) unchanged. A block reduces damage and precludes a crit.
            ShieldBlockResolver.ShieldBlock shieldBlock = shieldBlockResolver.resolve(target);
            if (shieldBlock.canBlock()) {
                int blockRoll = random.roll(1, 100);
                blocked = blockRoll <= clamp(shieldBlock.chancePercent(), 0, 100);
            }
            if (blocked) {
                int reduction = clamp(shieldBlock.reductionPercent(), 0, 100);
                adjusted = (int) Math.round(adjusted * ((100 - reduction) / 100.0));
            } else {
                int critChance = CombatSettings.baseCritChance()
                    + attack.critBonus();
                critChance = attackerMods.critChance().apply(critChance);
                critChance = clamp(critChance, 0, 100);
                int critRoll = random.roll(1, 100);
                crit = critRoll <= critChance;
                if (crit) {
                    adjusted *= CombatSettings.critMultiplier();
                }
            }
            if (offhand) {
                adjusted = (int) Math.round(adjusted * (CombatSettings.offhandDamagePercent() / 100.0));
            }
            damage = Math.max(0, adjusted);
        }

        Player updatedTarget = hit && damage > 0
            ? target.withVitals(target.getVitals().damage(damage))
            : target;

        List<String> effectTargetMessages = new ArrayList<>();
        List<String> effectRoomMessages = new ArrayList<>();
        if (hit && effectEngine != null && attack.effectOnHit() != null) {
            AttackEffectApplication effectApplication = attack.effectOnHit();
            int effectRoll = random.roll(1, 100);
            if (effectRoll <= effectApplication.chancePercent()) {
                effectEngine.apply(updatedTarget, effectApplication.effectId(), new EffectMessageSink() {
                    @Override
                    public void sendToTarget(String message) {
                        if (message != null && !message.isBlank()) {
                            effectTargetMessages.add(message);
                        }
                    }

                    @Override
                    public void sendToRoom(String message) {
                        if (message != null && !message.isBlank()) {
                            effectRoomMessages.add(message);
                        }
                    }
                });
            }
        }

        String sourceMessage;
        String targetMessage;
        String roomMessage;
        String targetName = target.getUsername().getValue();
        String attackerName = attacker.getUsername().getValue();
        if (offhand) {
            // Off-hand swings always use dedicated, weapon-named messages so a player can tell the
            // second hit of the round apart from their main-hand attack.
            OffhandMessages offhandMessages =
                offhandMessages(offhandWeaponName, attackerName, targetName, hit, blocked, crit, damage);
            sourceMessage = offhandMessages.source();
            targetMessage = offhandMessages.target();
            roomMessage = offhandMessages.room();
        } else {
            sourceMessage = null;
            targetMessage = null;
            roomMessage = null;
            List<MessageSpec> specs = attack.messages();
            if (!specs.isEmpty()) {
                MessagePhase phase;
                if (!hit) {
                    phase = MessagePhase.ATTACK_MISS;
                } else if (blocked) {
                    phase = MessagePhase.ATTACK_BLOCK;
                } else if (crit) {
                    phase = MessagePhase.ATTACK_CRIT;
                } else {
                    phase = MessagePhase.ATTACK_HIT;
                }
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
            } else if (blocked) {
                sourceMessage = targetName + " blocks your attack.";
                targetMessage = "You block " + attackerName + "'s attack with your shield.";
                roomMessage = targetName + " blocks " + attackerName + "'s attack.";
            } else if (crit) {
                sourceMessage = "You critically hit " + targetName + " for " + damage + ".";
                targetMessage = attackerName + " critically hits you for " + damage + ".";
                roomMessage = attackerName + " critically hits " + targetName + ".";
            } else {
                sourceMessage = "You hit " + targetName + " for " + damage + ".";
                targetMessage = attackerName + " hits you for " + damage + ".";
                roomMessage = attackerName + " hits " + targetName + ".";
            }
        }

        return new AttackOutcome(updatedTarget, hit, crit, blocked, damage,
            sourceMessage, targetMessage, roomMessage, effectTargetMessages, effectRoomMessages);
    }

    private OffhandMessages offhandMessages(
        String weaponName, String attackerName, String targetName,
        boolean hit, boolean blocked, boolean crit, int damage) {
        String weapon = "off-hand " + weaponName;
        if (!hit) {
            return new OffhandMessages(
                "Your " + weapon + " misses " + targetName + ".",
                attackerName + "'s " + weapon + " misses you.",
                attackerName + "'s " + weapon + " misses " + targetName + ".");
        }
        if (blocked) {
            return new OffhandMessages(
                targetName + " blocks your " + weapon + ".",
                "You block " + attackerName + "'s " + weapon + " with your shield.",
                targetName + " blocks " + attackerName + "'s " + weapon + ".");
        }
        if (crit) {
            return new OffhandMessages(
                "Your " + weapon + " critically hits " + targetName + " for " + damage + ".",
                attackerName + "'s " + weapon + " critically hits you for " + damage + ".",
                attackerName + "'s " + weapon + " critically hits " + targetName + ".");
        }
        return new OffhandMessages(
            "Your " + weapon + " hits " + targetName + " for " + damage + ".",
            attackerName + "'s " + weapon + " hits you for " + damage + ".",
            attackerName + "'s " + weapon + " hits " + targetName + ".");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The outcome of resolving a single attack (main-hand or off-hand): the updated target plus the
     * hit/crit/block/damage flags and the three rendered messages and any on-hit effect messages.
     */
    private record AttackOutcome(
        Player updatedTarget,
        boolean hit,
        boolean crit,
        boolean blocked,
        int damage,
        String sourceMessage,
        String targetMessage,
        String roomMessage,
        List<String> effectTargetMessages,
        List<String> effectRoomMessages
    ) {
    }

    private record OffhandMessages(String source, String target, String room) {
    }
}
