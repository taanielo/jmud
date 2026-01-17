package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.RoomId;

class CharacterTest {

    @Test
    void createsCharacterInRoom() {
        Character character = new BaseCharacter(
            CharacterId.of("npc-1"),
            "Guard",
            RoomId.of("training-yard"),
            new SimpleInventory(),
            BasicStats.of(10, 10, 5, 5, 2, 3),
            List.of(StatusEffect.of("alert"))
        );

        assertEquals("npc-1", character.id().getValue());
        assertEquals("training-yard", character.currentRoomId().getValue());
        assertEquals("Guard", character.name());
    }

    @Test
    void inventorySupportsAddRemove() {
        Inventory inventory = new SimpleInventory();
        Inventory withItem = inventory.add("apple");

        assertTrue(withItem.contains("apple"));
        assertEquals(1, withItem.size());

        Inventory removed = withItem.remove("apple");
        assertEquals(0, removed.size());
    }
}
