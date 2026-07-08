package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.messaging.MessageSpecMapper;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemEffect;
import io.taanielo.jmud.core.world.ItemEffectOperation;
import io.taanielo.jmud.core.world.ItemId;

public class ItemMapper {

    public ItemDto toDto(Item item) {
        Objects.requireNonNull(item, "Item is required");
        ItemAttributesDto attributes = new ItemAttributesDto(item.getAttributes().getStats());
        List<ItemEffectDto> effects = item.getEffects().stream()
            .map(effect -> new ItemEffectDto(effect.id().getValue(), effect.durationTicks(), effect.operation()))
            .toList();
        List<ItemDto> contents = item.getContainedItems().isEmpty()
            ? null
            : item.getContainedItems().stream().map(this::toDto).toList();
        return new ItemDto(
            SchemaVersions.V5,
            item.getId().getValue(),
            item.getName(),
            item.getDescription(),
            attributes,
            effects,
            MessageSpecMapper.toDtos(item.getMessages()),
            item.getEquipSlot() == null ? null : item.getEquipSlot().id(),
            item.getWeight(),
            item.getValue(),
            item.getAttackRef() == null ? null : item.getAttackRef().getValue(),
            item.getTeachesAbilityRef() == null ? null : item.getTeachesAbilityRef().getValue(),
            item.getContainerCapacity(),
            contents
        );
    }

    public Item toDomain(ItemDto dto) {
        Objects.requireNonNull(dto, "Item DTO is required");
        ItemAttributesDto attributesDto = Objects.requireNonNull(dto.attributes(), "Item attributes are required");
        ItemAttributes attributes = new ItemAttributes(attributesDto.stats());
        List<ItemEffect> effects = dto.effects().stream()
            .map(effect -> new ItemEffect(
                EffectId.of(effect.effectId()),
                effect.durationTicks(),
                effect.op() == null ? ItemEffectOperation.APPLY : effect.op()
            ))
            .toList();
        List<MessageSpec> messages = MessageSpecMapper.fromDtos(dto.messages());
        EquipmentSlot slot = EquipmentSlot.fromId(dto.equipSlot());
        AttackId attackRef = dto.attackRef() != null ? AttackId.of(dto.attackRef()) : null;
        AbilityId teachesAbilityRef = dto.teachesAbilityRef() != null ? AbilityId.of(dto.teachesAbilityRef()) : null;
        List<Item> contents = dto.contents() == null
            ? List.of()
            : dto.contents().stream().map(this::toDomain).toList();
        return new Item(
            ItemId.of(dto.id()),
            dto.name(),
            dto.description(),
            attributes,
            effects,
            messages,
            slot,
            dto.weight(),
            dto.value(),
            attackRef,
            teachesAbilityRef,
            dto.containerCapacity(),
            contents
        );
    }
}
