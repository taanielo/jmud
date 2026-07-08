package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class JsonItemRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsItem() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item item = new Item(
            ItemId.of("rusty-dagger"),
            "Rusty Dagger",
            "A dull blade with a chipped edge.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            2,
            3,
            null
        );
        repository.save(item);

        Optional<Item> loaded = repository.findById(ItemId.of("rusty-dagger"));

        assertTrue(loaded.isPresent());
        assertEquals(item, loaded.get());
    }

    @Test
    void savesAndLoadsContainerWithContents() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item apple = new Item(
            ItemId.of("apple"),
            "an apple",
            "A crisp red apple.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            2,
            null
        );
        Item bag = new Item(
            ItemId.of("leather-bag"),
            "a leather bag",
            "A supple leather bag.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            20,
            null,
            null,
            5,
            List.of(apple)
        );
        repository.save(bag);

        // Fresh repository (no cache) so the values come back off disk.
        Optional<Item> loaded = new JsonItemRepository(dataRoot).findById(ItemId.of("leather-bag"));

        assertTrue(loaded.isPresent());
        Item reloaded = loaded.get();
        assertTrue(reloaded.isContainer());
        assertEquals(Integer.valueOf(5), reloaded.getContainerCapacity());
        assertEquals(1, reloaded.containedItemCount());
        assertEquals(ItemId.of("apple"), reloaded.getContainedItems().getFirst().getId());
        assertEquals(bag, reloaded);
    }

    @Test
    void savesAndLoadsDurableItem() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item sword = new Item(
            ItemId.of("iron-sword"),
            "Iron Sword",
            "A plain iron sword.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            io.taanielo.jmud.core.world.EquipmentSlot.WEAPON,
            5,
            25,
            null,
            null,
            null,
            List.of(),
            null,
            50,
            30
        );
        repository.save(sword);

        // Fresh repository (no cache) so the values come back off disk.
        Optional<Item> loaded = new JsonItemRepository(dataRoot).findById(ItemId.of("iron-sword"));

        assertTrue(loaded.isPresent());
        Item reloaded = loaded.get();
        assertTrue(reloaded.isBreakable());
        assertEquals(Integer.valueOf(50), reloaded.getMaxDurability());
        assertEquals(Integer.valueOf(30), reloaded.getDurability());
        assertEquals(sword, reloaded);
    }

    @Test
    void savesAndLoadsRareItemWithAffixes() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item blade = new Item(
            ItemId.of("runed-blade"),
            "Runed Blade",
            "A blade humming with power.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            io.taanielo.jmud.core.world.EquipmentSlot.WEAPON,
            5,
            120,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            io.taanielo.jmud.core.world.Rarity.RARE,
            List.of(
                io.taanielo.jmud.core.world.AffixId.of("of-the-bear"),
                io.taanielo.jmud.core.world.AffixId.of("of-vitality"))
        );
        repository.save(blade);

        // Fresh repository (no cache) so the values come back off disk.
        Optional<Item> loaded = new JsonItemRepository(dataRoot).findById(ItemId.of("runed-blade"));

        assertTrue(loaded.isPresent());
        Item reloaded = loaded.get();
        assertEquals(io.taanielo.jmud.core.world.Rarity.RARE, reloaded.getRarity());
        assertEquals(
            List.of(
                io.taanielo.jmud.core.world.AffixId.of("of-the-bear"),
                io.taanielo.jmud.core.world.AffixId.of("of-vitality")),
            reloaded.getAffixes());
        assertEquals(blade, reloaded);
    }

    @Test
    void throwsOnInvalidJson() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        Path itemsDir = dataRoot.resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("broken.json"), "{ not-valid-json");

        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        RepositoryException error = assertThrows(
            RepositoryException.class,
            () -> repository.findById(ItemId.of("broken"))
        );

        assertTrue(error.getMessage().contains("Failed to read item data"));
    }
}
