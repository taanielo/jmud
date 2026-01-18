package io.taanielo.jmud.core.combat;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.ModifierOperation;

/**
 * Resolves combat modifiers from active effects.
 */
public class CombatModifierResolver {
    private final EffectRepository repository;

    /**
     * Creates a resolver backed by the provided effect repository.
     */
    public CombatModifierResolver(EffectRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Effect repository is required");
    }

    /**
     * Resolves combat modifiers from the active effect instances.
     */
    public CombatModifiers resolve(List<EffectInstance> effects) throws EffectRepositoryException {
        Objects.requireNonNull(effects, "Effects are required");
        int attackAdd = 0;
        int defenseAdd = 0;
        int damageAdd = 0;
        int hitChanceAdd = 0;
        int critChanceAdd = 0;
        int attackMultiplier = 1;
        int defenseMultiplier = 1;
        int damageMultiplier = 1;
        int hitChanceMultiplier = 1;
        int critChanceMultiplier = 1;
        for (EffectInstance instance : effects) {
            EffectDefinition definition = repository.findById(instance.id())
                .orElseThrow(() -> new EffectRepositoryException("Unknown effect id " + instance.id().getValue()));
            int stacks = Math.max(1, instance.stacks());
            for (EffectModifier modifier : definition.modifiers()) {
                String stat = modifier.stat().trim().toLowerCase();
                int amount = modifier.amount();
                if (modifier.operation() == ModifierOperation.ADD) {
                    int total = amount * stacks;
                    switch (stat) {
                        case "attack" -> attackAdd += total;
                        case "defense" -> defenseAdd += total;
                        case "damage" -> damageAdd += total;
                        case "hit_chance" -> hitChanceAdd += total;
                        case "crit_chance" -> critChanceAdd += total;
                        default -> {
                            // ignore unrelated modifiers
                        }
                    }
                    continue;
                }
                if (modifier.operation() == ModifierOperation.MULTIPLY) {
                    for (int i = 0; i < stacks; i += 1) {
                        switch (stat) {
                            case "attack" -> attackMultiplier = multiplySafely(attackMultiplier, amount);
                            case "defense" -> defenseMultiplier = multiplySafely(defenseMultiplier, amount);
                            case "damage" -> damageMultiplier = multiplySafely(damageMultiplier, amount);
                            case "hit_chance" -> hitChanceMultiplier = multiplySafely(hitChanceMultiplier, amount);
                            case "crit_chance" -> critChanceMultiplier = multiplySafely(critChanceMultiplier, amount);
                            default -> {
                                // ignore unrelated modifiers
                            }
                        }
                    }
                }
            }
        }
        return new CombatModifiers(
            new CombatStatModifier(attackAdd, attackMultiplier),
            new CombatStatModifier(defenseAdd, defenseMultiplier),
            new CombatStatModifier(damageAdd, damageMultiplier),
            new CombatStatModifier(hitChanceAdd, hitChanceMultiplier),
            new CombatStatModifier(critChanceAdd, critChanceMultiplier)
        );
    }

    private int multiplySafely(int current, int factor) {
        long result = (long) current * factor;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }
}
