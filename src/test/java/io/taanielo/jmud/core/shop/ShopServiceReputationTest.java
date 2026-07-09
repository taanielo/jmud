package io.taanielo.jmud.core.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.FactionRepository;
import io.taanielo.jmud.core.faction.FactionRepositoryException;
import io.taanielo.jmud.core.faction.PlayerReputation;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies that {@link ShopService} shifts buy and sell prices based on the player's reputation with
 * the shop's faction, via the injected {@link io.taanielo.jmud.core.faction.ReputationPriceResolver}.
 */
class ShopServiceReputationTest {

    private static final RoomId SHOP_ROOM = RoomId.of("armory");
    private static final FactionId MERCHANTS = FactionId.of("merchants");
    private static final Item IRON_SWORD = item("iron-sword", "Iron Sword", "A plain iron sword.", 100);

    private Shop shop;
    private ShopService shopService;

    @BeforeEach
    void setUp() throws FactionRepositoryException {
        List<StockEntry> stock = List.of(new StockEntry(IRON_SWORD.getId(), null));
        shop = new Shop(ShopId.of("armory-shop"), "Torbal", SHOP_ROOM, stock, 0.5, MERCHANTS);
        ItemRepository itemRepository = new StubItemRepository(Map.of(IRON_SWORD.getId(), IRON_SWORD));
        ShopRepository shopRepository = new StubShopRepository(List.of(shop));
        Faction merchants = new Faction(MERCHANTS, "the Merchants' Guild", "Traders.", -10, 0, 0.02);
        ReputationService reputationService =
            new ReputationService(new StubFactionRepository(List.of(merchants)));
        shopService = new ShopService(shopRepository, itemRepository, reputationService);
    }

    @Test
    void buy_isCheaperForFriendlyReputation() {
        Player friendly = playerWithGold(200).withReputation(standing(10));

        ShopTransactionResult result = shopService.buy(friendly, shop, "iron sword");

        assertTrue(result.success(), result.message());
        // 100 * (1 - 10*0.02) = 80.
        assertEquals(80, friendly.getGold() - result.updatedPlayer().getGold());
    }

    @Test
    void buy_isDearerForHostileReputation() {
        Player hostile = playerWithGold(200).withReputation(standing(-10));

        ShopTransactionResult result = shopService.buy(hostile, shop, "iron sword");

        assertTrue(result.success(), result.message());
        // 100 * (1 + 10*0.02) = 120.
        assertEquals(120, hostile.getGold() - result.updatedPlayer().getGold());
    }

    @Test
    void sell_paysMoreForFriendlyReputation() {
        Player friendly = playerWithGold(0).addItem(IRON_SWORD).withReputation(standing(10));

        ShopTransactionResult result = shopService.sell(friendly, shop, "iron sword");

        assertTrue(result.success(), result.message());
        // base = floor(100 * 0.5) = 50; friendly = 50 * (1 + 10*0.02) = 60.
        assertEquals(60, result.updatedPlayer().getGold());
    }

    @Test
    void listing_reflectsAdjustedBuyPrice() {
        Player friendly = playerWithGold(0).withReputation(standing(10));

        List<String> lines = shopService.formatListing(shop, friendly);
        String joined = String.join("\n", lines);

        assertTrue(joined.contains("80 gold"), "Listing should show the discounted 80 gold price: " + joined);
    }

    private static PlayerReputation standing(int value) {
        return PlayerReputation.empty().adjust(MERCHANTS, value);
    }

    private static Item item(String id, String name, String description, int value) {
        return Item.builder(ItemId.of(id), name, description, ItemAttributes.empty())
            .weight(1)
            .value(value)
            .build();
    }

    private static Player playerWithGold(int gold) {
        User user = User.of(Username.of("tester"), Password.hash("pw", 1));
        return Player.of(user, "%hp> ").withGold(gold);
    }

    private record StubFactionRepository(List<Faction> factions) implements FactionRepository {
        @Override
        public List<Faction> findAll() {
            return factions;
        }

        @Override
        public Optional<Faction> findById(FactionId factionId) {
            return factions.stream().filter(f -> f.id().equals(factionId)).findFirst();
        }
    }

    private record StubShopRepository(List<Shop> shops) implements ShopRepository {
        @Override
        public List<Shop> findAll() {
            return shops;
        }

        @Override
        public Optional<Shop> findByRoomId(RoomId roomId) {
            return shops.stream().filter(s -> s.roomId().equals(roomId)).findFirst();
        }
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
