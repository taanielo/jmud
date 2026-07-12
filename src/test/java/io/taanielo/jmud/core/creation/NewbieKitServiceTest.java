package io.taanielo.jmud.core.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Unit tests for {@link NewbieKitService} using a stub item repository (no networking or file I/O).
 */
class NewbieKitServiceTest {

    private static final Item BREAD = item("bread", "Loaf of Bread");
    private static final Item WATER = item("water", "Waterskin");

    @Test
    void applyTo_addsStartingGold() {
        NewbieKit kit = new NewbieKit(40, List.of());
        NewbieKitService service = new NewbieKitService(kit, stubRepo());

        Player result = service.applyTo(newPlayer());

        assertEquals(40, result.getGold());
        assertTrue(result.getInventory().isEmpty());
    }

    @Test
    void applyTo_addsStartingItems() {
        NewbieKit kit = new NewbieKit(0,
            List.of(ItemId.of("bread"), ItemId.of("bread"), ItemId.of("water"), ItemId.of("water")));
        NewbieKitService service = new NewbieKitService(kit, stubRepo());

        Player result = service.applyTo(newPlayer());

        assertEquals(0, result.getGold());
        List<Item> inventory = result.getInventory();
        assertEquals(4, inventory.size(), "all four provisions must be granted");
        assertEquals(2, inventory.stream().filter(i -> i.getId().equals(ItemId.of("bread"))).count());
        assertEquals(2, inventory.stream().filter(i -> i.getId().equals(ItemId.of("water"))).count());
    }

    @Test
    void applyTo_grantsGoldAndItemsTogether() {
        NewbieKit kit = new NewbieKit(40, List.of(ItemId.of("bread"), ItemId.of("water")));
        NewbieKitService service = new NewbieKitService(kit, stubRepo());

        Player result = service.applyTo(newPlayer());

        assertEquals(40, result.getGold());
        assertEquals(2, result.getInventory().size());
    }

    @Test
    void applyTo_skipsUnknownItemWithoutFailing() {
        NewbieKit kit = new NewbieKit(10, List.of(ItemId.of("bread"), ItemId.of("does-not-exist")));
        NewbieKitService service = new NewbieKitService(kit, stubRepo());

        Player result = service.applyTo(newPlayer());

        assertEquals(10, result.getGold());
        assertEquals(1, result.getInventory().size(), "the unknown item is skipped, not fatal");
        assertEquals(ItemId.of("bread"), result.getInventory().getFirst().getId());
    }

    @Test
    void applyTo_emptyKitLeavesPlayerUnchanged() {
        NewbieKitService service = new NewbieKitService(NewbieKit.EMPTY, stubRepo());
        Player player = newPlayer();

        Player result = service.applyTo(player);

        assertEquals(0, result.getGold());
        assertTrue(result.getInventory().isEmpty());
    }

    private static Player newPlayer() {
        User user = User.of(Username.of("sparky"), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private static ItemRepository stubRepo() {
        return new StubItemRepository(Map.of(BREAD.getId(), BREAD, WATER.getId(), WATER));
    }

    private static Item item(String id, String name) {
        return Item.builder(ItemId.of(id), name, name, ItemAttributes.empty())
            .weight(1)
            .value(5)
            .build();
    }

    private record StubItemRepository(Map<ItemId, Item> items) implements ItemRepository {
        @Override
        public void save(Item item) {
        }

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.ofNullable(items.get(id));
        }
    }
}
