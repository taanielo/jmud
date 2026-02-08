package io.taanielo.jmud.core.ability.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCooldown;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityDefinition;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.messaging.MessageSpecMapper;

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
            SchemaVersions.V2,
            ability.id().getValue(),
            ability.name(),
            ability.type(),
            ability.level(),
            new AbilityCostDto(ability.cost().mana(), ability.cost().move()),
            new AbilityCooldownDto(ability.cooldown().ticks()),
            ability.targeting(),
            ability.aliases(),
            effects,
            MessageSpecMapper.toDtos(ability.messages())
        );
    }

    public Ability toDomain(AbilityDto dto) {
        Objects.requireNonNull(dto, "Ability DTO is required");
        AbilityCostDto costDto = dto.cost();
        AbilityCooldownDto cooldownDto = Objects.requireNonNull(dto.cooldown(), "Ability cooldown is required");
        List<AbilityEffectDto> effectDtos = Objects.requireNonNull(dto.effects(), "Ability effects are required");
        List<AbilityEffect> effects = effectDtos.stream()
            .map(this::mapEffect)
            .toList();
        int manaCost = 0;
        int moveCost = 0;
        if (costDto != null) {
            if (costDto.mana() != null) {
                manaCost = costDto.mana();
            }
            if (costDto.move() != null) {
                moveCost = costDto.move();
            }
        }
        List<MessageSpec> messages = MessageSpecMapper.fromDtos(dto.messages());
        return new AbilityDefinition(
            AbilityId.of(dto.id()),
            dto.name(),
            dto.type(),
            dto.level(),
            new AbilityCost(manaCost, moveCost),
            new AbilityCooldown(cooldownDto.ticks()),
            dto.targeting(),
            dto.aliases(),
            effects,
            messages
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
