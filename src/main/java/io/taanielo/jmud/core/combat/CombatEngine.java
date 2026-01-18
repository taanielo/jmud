package io.taanielo.jmud.core.combat;

import java.util.Objects;

import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.player.Player;

/**
 * Resolves deterministic combat results for a single action.
 */
public class CombatEngine {
    private final AttackRepository attackRepository;
    private final CombatModifierResolver modifierResolver;
    private final CombatRandom random;

    /**
     * Creates a combat engine with the provided dependencies.
     */
    public CombatEngine(
        AttackRepository attackRepository,
        CombatModifierResolver modifierResolver,
        CombatRandom random
    ) {
        this.attackRepository = Objects.requireNonNull(attackRepository, "Attack repository is required");
        this.modifierResolver = Objects.requireNonNull(modifierResolver, "Modifier resolver is required");
        this.random = Objects.requireNonNull(random, "Combat random is required");
    }

    /**
     * Resolves a single combat action between the attacker and target.
     */
    public CombatResult resolve(Player attacker, Player target, AttackId attackId)
        throws AttackRepositoryException, EffectRepositoryException {
        return resolve(new CombatAction(attacker, target, attackId));
    }

    /**
     * Resolves a single combat action between the attacker and target.
     */
    public CombatResult resolve(CombatAction action)
        throws AttackRepositoryException, EffectRepositoryException {
        Objects.requireNonNull(action, "Combat action is required");
        Player attacker = action.attacker();
        Player target = action.target();
        AttackId attackId = action.attackId();

        AttackDefinition attack = attackRepository.findById(attackId)
            .orElseThrow(() -> new AttackRepositoryException("Unknown attack id " + attackId.getValue()));
        CombatModifiers attackerMods = modifierResolver.resolve(attacker.effects());
        CombatModifiers targetMods = modifierResolver.resolve(target.effects());

        int hitChanceBase = CombatSettings.baseHitChance()
            + attack.hitBonus()
            + attackerMods.attack().apply(0)
            - targetMods.defense().apply(0);
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

        String sourceMessage;
        String targetMessage;
        String roomMessage;
        String targetName = target.getUsername().getValue();
        String attackerName = attacker.getUsername().getValue();
        if (!hit) {
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

        return new CombatResult(attacker, updatedTarget, hit, crit, damage, sourceMessage, targetMessage, roomMessage);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
