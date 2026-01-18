package io.taanielo.jmud.core.ability;

import java.util.List;
import java.util.Objects;

public class AbilityDefinition implements Ability {
    private final String id;
    private final String name;
    private final AbilityType type;
    private final int level;
    private final AbilityCost cost;
    private final AbilityCooldown cooldown;
    private final AbilityTargeting targeting;
    private final List<String> aliases;
    private final List<AbilityEffect> effects;

    public AbilityDefinition(
        String id,
        String name,
        AbilityType type,
        int level,
        AbilityCost cost,
        AbilityCooldown cooldown,
        AbilityTargeting targeting,
        List<String> aliases,
        List<AbilityEffect> effects
    ) {
        this.id = validateText(id, "Ability id");
        this.name = validateText(name, "Ability name");
        this.type = Objects.requireNonNull(type, "Ability type is required");
        if (level <= 0) {
            throw new IllegalArgumentException("Ability level must be positive");
        }
        this.level = level;
        this.cost = Objects.requireNonNull(cost, "Ability cost is required");
        this.cooldown = Objects.requireNonNull(cooldown, "Ability cooldown is required");
        this.targeting = Objects.requireNonNull(targeting, "Ability targeting is required");
        this.aliases = List.copyOf(Objects.requireNonNullElse(aliases, List.of()));
        this.effects = List.copyOf(Objects.requireNonNullElse(effects, List.of()));
        if (this.effects.isEmpty()) {
            throw new IllegalArgumentException("Ability must define at least one effect");
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AbilityType type() {
        return type;
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public AbilityCost cost() {
        return cost;
    }

    @Override
    public AbilityCooldown cooldown() {
        return cooldown;
    }

    @Override
    public AbilityTargeting targeting() {
        return targeting;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    @Override
    public List<AbilityEffect> effects() {
        return effects;
    }

    private String validateText(String value, String label) {
        String trimmed = Objects.requireNonNull(value, label + " is required").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return trimmed;
    }
}
