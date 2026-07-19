package io.taanielo.jmud.core.world.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.ItemSetId;

class ItemMapperSetTest {

    private final ItemMapper mapper = new ItemMapper();

    @Test
    void setItemRoundTripsPreservingSetIdAtV16() {
        Item piece = Item.builder(ItemId.of("leather-cap"), "Leather Cap", "A cap.",
                new ItemAttributes(Map.of("ac", 2)))
            .equipSlot(EquipmentSlot.HEAD)
            .weight(2).value(20)
            .setId(ItemSetId.of("wayfarers-leathers"))
            .build();

        ItemDto dto = mapper.toDto(piece);
        assertEquals(SchemaVersions.V16, dto.schemaVersion());
        assertEquals("wayfarers-leathers", dto.setId());

        Item restored = mapper.toDomain(dto);
        assertEquals(ItemSetId.of("wayfarers-leathers"), restored.getSetId());
    }

    @Test
    void plainItemHasNoSetId() {
        Item plain = Item.builder(ItemId.of("bread"), "Bread", "A loaf.", ItemAttributes.empty())
            .weight(1).value(5).build();

        ItemDto dto = mapper.toDto(plain);
        assertNull(dto.setId());
        assertNull(mapper.toDomain(dto).getSetId());
    }
}
