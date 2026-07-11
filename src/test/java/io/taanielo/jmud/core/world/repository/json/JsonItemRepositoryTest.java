package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

        Item item = Item.builder(
            ItemId.of("rusty-dagger"), "Rusty Dagger", "A dull blade with a chipped edge.", ItemAttributes.empty())
            .weight(2)
            .value(3)
            .build();
        repository.save(item);

        Optional<Item> loaded = repository.findById(ItemId.of("rusty-dagger"));

        assertTrue(loaded.isPresent());
        assertEquals(item, loaded.get());
    }

    @Test
    void savesAndLoadsContainerWithContents() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item apple = Item.builder(ItemId.of("apple"), "an apple", "A crisp red apple.", ItemAttributes.empty())
            .weight(1)
            .value(2)
            .build();
        Item bag = Item.builder(
            ItemId.of("leather-bag"), "a leather bag", "A supple leather bag.", ItemAttributes.empty())
            .weight(1)
            .value(20)
            .container(io.taanielo.jmud.core.world.ContainerState.of(5, List.of(apple)))
            .build();
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

        Item sword = Item.builder(
            ItemId.of("iron-sword"), "Iron Sword", "A plain iron sword.", ItemAttributes.empty())
            .equipSlot(io.taanielo.jmud.core.world.EquipmentSlot.WEAPON)
            .weight(5)
            .value(25)
            .durability(io.taanielo.jmud.core.world.Durability.of(50, 30))
            .build();
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
    void savesAndLoadsTwoHandedWeapon() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item greataxe = Item.builder(
            ItemId.of("greataxe"), "a greataxe", "A massive two-handed axe.", ItemAttributes.empty())
            .equipSlot(io.taanielo.jmud.core.world.EquipmentSlot.WEAPON)
            .weight(6)
            .value(100)
            .twoHanded(true)
            .build();
        repository.save(greataxe);

        // Fresh repository (no cache) so the flag comes back off disk.
        Optional<Item> loaded = new JsonItemRepository(dataRoot).findById(ItemId.of("greataxe"));

        assertTrue(loaded.isPresent());
        Item reloaded = loaded.get();
        assertTrue(reloaded.isTwoHanded());
        assertEquals(greataxe, reloaded);
    }

    @Test
    void savesAndLoadsRareItemWithAffixes() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item blade = Item.builder(
            ItemId.of("runed-blade"), "Runed Blade", "A blade humming with power.", ItemAttributes.empty())
            .equipSlot(io.taanielo.jmud.core.world.EquipmentSlot.WEAPON)
            .weight(5)
            .value(120)
            .rarity(io.taanielo.jmud.core.world.RarityProfile.of(
                io.taanielo.jmud.core.world.Rarity.RARE,
                List.of(
                    io.taanielo.jmud.core.world.AffixId.of("of-the-bear"),
                    io.taanielo.jmud.core.world.AffixId.of("of-vitality"))))
            .build();
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

    /**
     * Golden-file round trip: loading the committed, byte-for-byte-unchanged
     * {@code data/items/wooden-chest.json} through {@link JsonItemRepository} must produce the same
     * {@link Item} state as before the component-record construction refactor. This guards the
     * persistence compatibility contract (AGENTS.md §11): the flat DTO fields still assemble into an
     * equivalent domain object via the new builder-based mapper.
     */
    @Test
    void loadsExistingWoodenChestDataFileUnchanged() throws Exception {
        Path source = Path.of("data", "items", "wooden-chest.json");
        assertTrue(Files.exists(source), "expected committed data file at " + source.toAbsolutePath());
        Path dataRoot = tempDir.resolve("data");
        Path itemsDir = dataRoot.resolve("items");
        Files.createDirectories(itemsDir);
        Files.copy(source, itemsDir.resolve("wooden-chest.json"), StandardCopyOption.REPLACE_EXISTING);

        JsonItemRepository repository = new JsonItemRepository(dataRoot);
        Item chest = repository.findById(ItemId.of("wooden-chest")).orElseThrow();

        assertEquals(ItemId.of("wooden-chest"), chest.getId());
        assertEquals("a wooden chest", chest.getName());
        assertEquals(20, chest.getWeight());
        assertEquals(60, chest.getValue());
        assertTrue(chest.isContainer());
        assertEquals(Integer.valueOf(10), chest.getContainerCapacity());
        assertTrue(chest.getContainedItems().isEmpty());
        assertFalse(chest.isLocked());
        assertTrue(chest.isIdentified());
        assertFalse(chest.isBreakable());
        assertNull(chest.getLightRadius());
        assertNull(chest.getEquipSlot());
        assertEquals(io.taanielo.jmud.core.world.Rarity.COMMON, chest.getRarity());
        assertTrue(chest.getAffixes().isEmpty());
        assertTrue(chest.getEffects().isEmpty());
    }
}
