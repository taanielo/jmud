package io.taanielo.jmud.core.ability;

import java.util.List;

public final class AbilityCatalog {
    private AbilityCatalog() {
    }

    public static AbilityRegistry defaultRegistry() {
        Ability bash = new AbilityDefinition(
            "skill.bash",
            "bash",
            AbilityType.SKILL,
            1,
            new AbilityCost(0, 3),
            new AbilityCooldown(3),
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffect(AbilityEffectType.DAMAGE, 4))
        );

        Ability fireball = new AbilityDefinition(
            "spell.fireball",
            "fireball",
            AbilityType.SPELL,
            1,
            new AbilityCost(3, 0),
            new AbilityCooldown(4),
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffect(AbilityEffectType.DAMAGE, 6))
        );

        Ability greaterFireball = new AbilityDefinition(
            "spell.fireball.greater",
            "greater fireball",
            AbilityType.SPELL,
            2,
            new AbilityCost(5, 0),
            new AbilityCooldown(5),
            AbilityTargeting.HARMFUL,
            List.of("fireball"),
            List.of(new AbilityEffect(AbilityEffectType.DAMAGE, 9))
        );

        Ability heal = new AbilityDefinition(
            "spell.heal",
            "heal",
            AbilityType.SPELL,
            1,
            new AbilityCost(4, 0),
            new AbilityCooldown(3),
            AbilityTargeting.BENEFICIAL,
            List.of("healing"),
            List.of(new AbilityEffect(AbilityEffectType.HEAL, 6))
        );

        return new AbilityRegistry(List.of(bash, fireball, greaterFireball, heal));
    }
}
