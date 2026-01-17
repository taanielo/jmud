package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

class CharacterTest {

    @Test
    void createsCharacterInRoom() {
        Character character = new BaseCharacter(
            CharacterId.of("npc-1"),
            "Guard",
            RoomId.of("training-yard"),
            new SimpleInventory(),
            new BasicStats(),
            List.of(StatusEffect.of("alert"))
        );

        assertEquals("npc-1", character.id().getValue());
        assertEquals("training-yard", character.currentRoomId().getValue());
        assertEquals("Guard", character.name());
    }

    @Test
    void inventoryAddsAndRemovesItems() {
        Inventory inventory = new SimpleInventory();
        ItemId itemId = ItemId.of("apple");
        inventory.add(new Item(itemId, "Apple", "A crisp apple."));

        assertEquals(1, inventory.size());
        assertTrue(inventory.remove(itemId));
        assertEquals(0, inventory.size());
    }
}
