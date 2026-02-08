package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.messaging.MessageSpecMapper;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemEffect;
import io.taanielo.jmud.core.world.ItemId;

public class ItemMapper {

    public ItemDto toDto(Item item) {
        Objects.requireNonNull(item, "Item is required");
        ItemAttributesDto attributes = new ItemAttributesDto(item.getAttributes().getStats());
        List<ItemEffectDto> effects = item.getEffects().stream()
            .map(effect -> new ItemEffectDto(effect.id().getValue(), effect.durationTicks()))
            .toList();
        return new ItemDto(
            SchemaVersions.V3,
            item.getId().getValue(),
            item.getName(),
            item.getDescription(),
            attributes,
            effects,
            MessageSpecMapper.toDtos(item.getMessages()),
            item.getEquipSlot() == null ? null : item.getEquipSlot().id(),
            item.getWeight(),
            item.getValue()
        );
    }

    public Item toDomain(ItemDto dto) {
        Objects.requireNonNull(dto, "Item DTO is required");
        ItemAttributesDto attributesDto = Objects.requireNonNull(dto.attributes(), "Item attributes are required");
        ItemAttributes attributes = new ItemAttributes(attributesDto.stats());
        List<ItemEffect> effects = dto.effects().stream()
            .map(effect -> new ItemEffect(EffectId.of(effect.effectId()), effect.durationTicks()))
            .toList();
        List<MessageSpec> messages = MessageSpecMapper.fromDtos(dto.messages());
        EquipmentSlot slot = EquipmentSlot.fromId(dto.equipSlot());
        return new Item(
            ItemId.of(dto.id()),
            dto.name(),
            dto.description(),
            attributes,
            effects,
            messages,
            slot,
            dto.weight(),
            dto.value()
        );
    }
}
