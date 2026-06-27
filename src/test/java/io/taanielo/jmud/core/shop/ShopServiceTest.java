package io.taanielo.jmud.core.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Unit tests for {@link ShopService}.
 */
class ShopServiceTest {

    private static final RoomId SHOP_ROOM = RoomId.of("armory");
    private static final RoomId OTHER_ROOM = RoomId.of("courtyard");

    // Items
    private static final Item IRON_SWORD = item("iron-sword", "Iron Sword",
        "A plain iron sword.", 25);
    private static final Item HEALTH_POTION = item("health-potion", "Health Potion",
        "A small red vial.", 10);

    // Shop
    private Shop shop;
    private StubItemRepository itemRepository;
    private StubShopRepository shopRepository;
    private ShopService shopService;

    @BeforeEach
    void setUp() {
        List<StockEntry> stock = List.of(
            new StockEntry(IRON_SWORD.getId(), null),     // uses item.value (25)
            new StockEntry(HEALTH_POTION.getId(), 8)      // explicit price override
        );
        shop = new Shop(ShopId.of("armory-shop"), "Torbal the Armorer", SHOP_ROOM, stock, 0.5);
        itemRepository = new StubItemRepository(
            Map.of(IRON_SWORD.getId(), IRON_SWORD, HEALTH_POTION.getId(), HEALTH_POTION));
        shopRepository = new StubShopRepository(List.of(shop));
        shopService = new ShopService(shopRepository, itemRepository);
    }

    // ── findShopInRoom ────────────────────────────────────────────────

    @Test
    void findShopInRoom_returnsShop_whenRoomMatches() {
        Optional<Shop> result = shopService.findShopInRoom(SHOP_ROOM);
        assertTrue(result.isPresent(), "Expected a shop in the armory");
        assertEquals("Torbal the Armorer", result.get().name());
    }

    @Test
    void findShopInRoom_returnsEmpty_whenNoShopInRoom() {
        Optional<Shop> result = shopService.findShopInRoom(OTHER_ROOM);
        assertTrue(result.isEmpty(), "Expected no shop in courtyard");
    }

    // ── formatListing ─────────────────────────────────────────────────

    @Test
    void formatListing_containsItemNames() {
        List<String> lines = shopService.formatListing(shop);
        String joined = String.join("\n", lines);
        assertTrue(joined.contains("Iron Sword"), "Listing should include Iron Sword");
        assertTrue(joined.contains("Health Potion"), "Listing should include Health Potion");
    }

    @Test
    void formatListing_showsOverridePrice() {
        List<String> lines = shopService.formatListing(shop);
        String joined = String.join("\n", lines);
        // Health Potion has explicit price of 8
        assertTrue(joined.contains("8 gold"), "Listing should show price override of 8 gold");
    }

    @Test
    void formatListing_showsItemValueWhenNoPriceOverride() {
        List<String> lines = shopService.formatListing(shop);
        String joined = String.join("\n", lines);
        // Iron Sword falls back to item.value = 25
        assertTrue(joined.contains("25 gold"), "Listing should show item value 25 gold for Iron Sword");
    }

    // ── buy ───────────────────────────────────────────────────────────

    @Test
    void buy_succeeds_whenPlayerHasSufficientGold() {
        Player player = playerWithGold(50);

        ShopTransactionResult result = shopService.buy(player, shop, "iron sword");

        assertTrue(result.success(), "Buy should succeed: " + result.message());
        assertNotNull(result.updatedPlayer(), "Updated player must not be null on success");
        assertEquals(25, player.getGold() - result.updatedPlayer().getGold(),
            "Expected 25 gold deducted");
        assertTrue(result.updatedPlayer().getInventory().stream()
                .anyMatch(i -> i.getId().equals(IRON_SWORD.getId())),
            "Iron Sword should be in player inventory after purchase");
    }

    @Test
    void buy_usesExplicitPriceOverride() {
        Player player = playerWithGold(10);

        // Health Potion has explicit price 8 (not 10)
        ShopTransactionResult result = shopService.buy(player, shop, "health potion");

        assertTrue(result.success(), "Buy should succeed with price 8: " + result.message());
        assertEquals(2, result.updatedPlayer().getGold(), "Expected 10 - 8 = 2 gold remaining");
    }

    @Test
    void buy_fails_whenPlayerCannotAfford() {
        Player player = playerWithGold(5);

        ShopTransactionResult result = shopService.buy(player, shop, "iron sword");

        assertFalse(result.success(), "Buy should fail when gold < price");
        assertTrue(result.message().contains("cannot afford"),
            "Error message should mention 'cannot afford': " + result.message());
    }

    @Test
    void buy_fails_whenItemNotInShopStock() {
        Player player = playerWithGold(100);

        ShopTransactionResult result = shopService.buy(player, shop, "magic wand");

        assertFalse(result.success());
        assertTrue(result.message().contains("does not carry"),
            "Error message should mention item not in stock: " + result.message());
    }

    @Test
    void buy_fails_whenArgBlank() {
        Player player = playerWithGold(50);

        ShopTransactionResult result = shopService.buy(player, shop, "");

        assertFalse(result.success());
        assertTrue(result.message().equalsIgnoreCase("buy what?"),
            "Expected 'Buy what?' prompt: " + result.message());
    }

    // ── sell ──────────────────────────────────────────────────────────

    @Test
    void sell_succeeds_removesItemAndAwardsGold() {
        Player player = playerWithGold(0).addItem(IRON_SWORD);

        ShopTransactionResult result = shopService.sell(player, shop, "iron sword");

        assertTrue(result.success(), "Sell should succeed: " + result.message());
        // 50% of 25 = 12
        assertEquals(12, result.updatedPlayer().getGold(), "Expected floor(25 * 0.5) = 12 gold");
        assertFalse(result.updatedPlayer().getInventory().stream()
                .anyMatch(i -> i.getId().equals(IRON_SWORD.getId())),
            "Iron Sword should be removed from inventory after selling");
    }

    @Test
    void sell_fails_whenItemNotInInventory() {
        Player player = playerWithGold(0);

        ShopTransactionResult result = shopService.sell(player, shop, "iron sword");

        assertFalse(result.success());
        assertTrue(result.message().contains("not carrying"),
            "Error should mention not carrying: " + result.message());
    }

    @Test
    void sell_fails_whenArgBlank() {
        Player player = playerWithGold(0);

        ShopTransactionResult result = shopService.sell(player, shop, "  ");

        assertFalse(result.success());
        assertTrue(result.message().equalsIgnoreCase("sell what?"),
            "Expected 'Sell what?' prompt: " + result.message());
    }

    @Test
    void sell_goldCalculationFloors() {
        // Item value 25, sell ratio 0.5 → floor(12.5) = 12
        Item oddItem = item("odd-item", "Odd Item", "desc", 25);
        Player player = playerWithGold(0).addItem(oddItem);
        itemRepository.put(oddItem.getId(), oddItem);

        // add odd-item to shop stock so the item repo has it
        List<StockEntry> stock = List.of(new StockEntry(oddItem.getId(), null));
        Shop localShop = new Shop(ShopId.of("test"), "Shopkeep", SHOP_ROOM, stock, 0.5);

        ShopTransactionResult result = shopService.sell(player, localShop, "odd item");

        assertTrue(result.success());
        assertEquals(12, result.updatedPlayer().getGold(),
            "Expected floor(25 * 0.5) = 12 gold");
    }

    // ── helpers ───────────────────────────────────────────────────────

    private static Item item(String id, String name, String description, int value) {
        return new Item(
            ItemId.of(id),
            name,
            description,
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            value,
            null
        );
    }

    private static Player playerWithGold(int gold) {
        User user = User.of(Username.of("tester"), Password.hash("pw", 1));
        return Player.of(user, "%hp> ").withGold(gold);
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private static class StubShopRepository implements ShopRepository {
        private final List<Shop> shops;
        StubShopRepository(List<Shop> shops) { this.shops = List.copyOf(shops); }

        @Override
        public List<Shop> findAll() { return shops; }

        @Override
        public Optional<Shop> findByRoomId(RoomId roomId) {
            return shops.stream().filter(s -> s.roomId().equals(roomId)).findFirst();
        }
    }

    private static class StubItemRepository implements ItemRepository {
        private final java.util.concurrent.ConcurrentHashMap<ItemId, Item> items;

        StubItemRepository(Map<ItemId, Item> initial) {
            items = new java.util.concurrent.ConcurrentHashMap<>(initial);
        }

        void put(ItemId id, Item item) { items.put(id, item); }

        @Override
        public void save(Item item) {}

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.ofNullable(items.get(id));
        }
    }
}
