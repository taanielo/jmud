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
            BasicStats.builder()
                .hp(10)
                .maxHp(10)
                .mana(5)
                .maxMana(5)
                .strength(2)
                .agility(3)
                .build(),
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
