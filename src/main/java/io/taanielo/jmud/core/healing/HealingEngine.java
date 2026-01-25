package io.taanielo.jmud.core.healing;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.ModifierOperation;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

public class HealingEngine {
    private final EffectRepository repository;

    public HealingEngine(EffectRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Effect repository is required");
    }

    public Player apply(Player player, int baseHealPerTick) throws EffectRepositoryException {
        Objects.requireNonNull(player, "Player is required");
        PlayerVitals vitals = player.getVitals();
        int effectiveMaxHp = applyModifiers(vitals.baseMaxHp(), player.effects(), HealingModifierKeys.MAX_HP);
        if (effectiveMaxHp < 1) {
            effectiveMaxHp = 1;
        }

        PlayerVitals updatedVitals = vitals;
        if (effectiveMaxHp != vitals.maxHp()) {
            updatedVitals = updatedVitals.withMaxHp(effectiveMaxHp);
        }

        int healing = applyModifiers(baseHealPerTick, player.effects(), HealingModifierKeys.HEAL_PER_TICK);
        if (healing > 0 && updatedVitals.hp() < updatedVitals.maxHp()) {
            updatedVitals = updatedVitals.heal(healing);
        }

        int damage = applyModifiers(0, player.effects(), HealingModifierKeys.DAMAGE_PER_TICK);
        if (damage > 0) {
            updatedVitals = updatedVitals.damage(damage);
        }

        if (updatedVitals == vitals) {
            return player;
        }
        return player.withVitals(updatedVitals);
    }

    private int applyModifiers(int base, List<EffectInstance> effects, String statKey) throws EffectRepositoryException {
        int addTotal = 0;
        int multiplyTotal = 1;
        for (EffectInstance instance : effects) {
            EffectDefinition definition = repository.findById(instance.id())
                .orElseThrow(() -> new EffectRepositoryException("Unknown effect id " + instance.id().getValue()));
            for (EffectModifier modifier : definition.modifiers()) {
                if (!modifier.stat().equalsIgnoreCase(statKey)) {
                    continue;
                }
                int stacks = Math.max(1, instance.stacks());
                if (modifier.operation() == ModifierOperation.ADD) {
                    addTotal += modifier.amount() * stacks;
                    continue;
                }
                if (modifier.operation() == ModifierOperation.MULTIPLY) {
                    for (int i = 0; i < stacks; i += 1) {
                        multiplyTotal *= modifier.amount();
                    }
                }
            }
        }

        long result = (long) base + addTotal;
        result *= multiplyTotal;
        if (result <= 0) {
            return 0;
        }
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) result;
    }
}
