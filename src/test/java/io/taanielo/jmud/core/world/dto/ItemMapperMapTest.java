package io.taanielo.jmud.core.world.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.area.AreaId;

class ItemMapperMapTest {

    private final ItemMapper mapper = new ItemMapper();

    @Test
    void mapItemRoundTripsPreservingAreaBinding() {
        Item map = Item.builder(ItemId.of("town-map"), "Map of Town", "A rough map.", ItemAttributes.empty())
            .weight(1).value(10).mapAreaId(AreaId.of("town")).build();

        ItemDto dto = mapper.toDto(map);
        assertEquals(SchemaVersions.V14, dto.schemaVersion());
        assertEquals("town", dto.mapAreaId());

        Item restored = mapper.toDomain(dto);
        assertTrue(restored.isMap());
        assertEquals(AreaId.of("town"), restored.getMapAreaId());
    }

    @Test
    void plainItemHasNoMapBinding() {
        Item plain = Item.builder(ItemId.of("bread"), "Bread", "A loaf.", ItemAttributes.empty())
            .weight(1).value(5).build();

        ItemDto dto = mapper.toDto(plain);
        assertEquals(null, dto.mapAreaId());
        assertTrue(!mapper.toDomain(dto).isMap());
    }
}
