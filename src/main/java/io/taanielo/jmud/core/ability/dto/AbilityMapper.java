package io.taanielo.jmud.core.ability.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCooldown;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityDefinition;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;

public class AbilityMapper {

    public AbilityDto toDto(Ability ability) {
        Objects.requireNonNull(ability, "Ability is required");
        List<AbilityEffectDto> effects = ability.effects().stream()
            .map(effect -> new AbilityEffectDto(
                effect.kind(),
                effect.stat(),
                effect.operation(),
                effect.amount(),
                effect.effectId()
            ))
            .toList();
        return new AbilityDto(
            SchemaVersions.V1,
            ability.id(),
            ability.name(),
            ability.type(),
            ability.level(),
            new AbilityCostDto(ability.cost().mana(), ability.cost().move()),
            new AbilityCooldownDto(ability.cooldown().ticks()),
            ability.targeting(),
            ability.aliases(),
            effects
        );
    }

    public Ability toDomain(AbilityDto dto) {
        Objects.requireNonNull(dto, "Ability DTO is required");
        AbilityCostDto costDto = Objects.requireNonNull(dto.cost(), "Ability cost is required");
        AbilityCooldownDto cooldownDto = Objects.requireNonNull(dto.cooldown(), "Ability cooldown is required");
        List<AbilityEffectDto> effectDtos = Objects.requireNonNull(dto.effects(), "Ability effects are required");
        List<AbilityEffect> effects = effectDtos.stream()
            .map(this::mapEffect)
            .toList();
        return new AbilityDefinition(
            dto.id(),
            dto.name(),
            dto.type(),
            dto.level(),
            new AbilityCost(costDto.mana(), costDto.move()),
            new AbilityCooldown(cooldownDto.ticks()),
            dto.targeting(),
            dto.aliases(),
            effects
        );
    }

    private AbilityEffect mapEffect(AbilityEffectDto dto) {
        Objects.requireNonNull(dto, "Ability effect is required");
        AbilityEffectKind kind = Objects.requireNonNull(dto.kind(), "Effect kind is required");
        if (kind == AbilityEffectKind.VITALS) {
            if (dto.amount() == null) {
                throw new IllegalArgumentException("Vitals effect amount is required");
            }
            return new AbilityEffect(
                kind,
                Objects.requireNonNull(dto.stat(), "Vitals effect stat is required"),
                Objects.requireNonNull(dto.operation(), "Vitals effect operation is required"),
                dto.amount(),
                null
            );
        }
        return new AbilityEffect(
            kind,
            null,
            null,
            0,
            dto.effectId()
        );
    }
}
