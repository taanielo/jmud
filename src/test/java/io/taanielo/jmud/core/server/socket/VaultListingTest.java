package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;

class VaultListingTest {

    private static Item item(String id, String name, int weight) {
        return Item.builder(ItemId.of(id), name, "A " + name + ".", ItemAttributes.empty())
            .weight(weight)
            .build();
    }

    @Test
    void emptyVaultShowsEmptyMessage() {
        List<String> lines = VaultListing.format(List.of(), 30);

        assertEquals(List.of("Your vault is empty."), lines);
    }

    @Test
    void listsItemsWithWeightAndSlotFooter() {
        List<String> lines = VaultListing.format(
            List.of(item("sword", "a sword", 5), item("shield", "a shield", 8)), 30);

        assertEquals("Your vault contains:", lines.get(0));
        assertTrue(lines.get(1).contains("a sword"));
        assertTrue(lines.get(1).contains("5 lb"));
        assertTrue(lines.get(3).contains("2 / 30"), "Footer should show slots used but was: " + lines.get(3));
    }
}
