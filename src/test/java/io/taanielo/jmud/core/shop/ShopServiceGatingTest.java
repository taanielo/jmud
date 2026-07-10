package io.taanielo.jmud.core.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * Verifies {@link ShopService} reputation-gating: stock entries carrying a {@code minReputation}
 * threshold are hidden behind a lock in listings and refused by {@code buy} for players whose standing
 * with the shop's faction is below the threshold, while ungated entries and faction-neutral shops keep
 * their historical behaviour.
 */
class ShopServiceGatingTest {

    private static final RoomId SHOP_ROOM = RoomId.of("shrouded-isle-cove");
    private static final FactionId BANDITS = FactionId.of("bandits");
    private static final Item TORCH = item("torch", "Torch", "A wooden torch.", 5);
    private static final Item TONIC = item("smugglers-tonic", "Smuggler's Tonic", "Black-market brew.", 40);

    private Shop shop;
    private ShopService shopService;

    @BeforeEach
    void setUp() throws FactionRepositoryException {
        // priceModifierPerPoint 0.0 keeps prices at base so gold assertions isolate the gate.
        List<StockEntry> stock = List.of(
            new StockEntry(TORCH.getId(), null),            // ungated
            new StockEntry(TONIC.getId(), null, 0)          // gated: requires standing >= 0
        );
        shop = new Shop(ShopId.of("cove-fence-shop"), "a shifty-eyed fence", SHOP_ROOM, stock, 0.5, BANDITS);
        ItemRepository itemRepository =
            new StubItemRepository(Map.of(TORCH.getId(), TORCH, TONIC.getId(), TONIC));
        ShopRepository shopRepository = new StubShopRepository(List.of(shop));
        Faction bandits = new Faction(BANDITS, "the Bandit Brotherhood", "Cutthroats.", -10, 0, 0.0);
        ReputationService reputationService =
            new ReputationService(new StubFactionRepository(List.of(bandits)));
        shopService = new ShopService(shopRepository, itemRepository, reputationService);
    }

    @Test
    void listing_marksGatedEntryLocked_whenStandingBelowThreshold() {
        Player hostile = playerWithGold(500).withReputation(standing(-25));

        String joined = String.join("\n", shopService.formatListing(shop, hostile));

        assertTrue(joined.contains("Torch"), "Ungated torch should still be listed: " + joined);
        assertTrue(joined.contains("[locked"), "Gated tonic should be marked locked: " + joined);
        assertTrue(joined.contains("bandits"), "Locked line should name the faction: " + joined);
        // The locked entry must not show a purchasable price.
        assertFalse(joined.contains("40 gold"), "Locked tonic must not show a price: " + joined);
    }

    @Test
    void listing_showsGatedEntry_whenStandingMeetsThreshold() {
        Player neutral = playerWithGold(500).withReputation(standing(0));

        String joined = String.join("\n", shopService.formatListing(shop, neutral));

        assertTrue(joined.contains("40 gold"), "Unlocked tonic should show its price: " + joined);
        assertFalse(joined.contains("[locked"), "Nothing should be locked at neutral standing: " + joined);
    }

    @Test
    void buy_rejectsLockedEntry_withoutMutatingPlayer() {
        Player hostile = playerWithGold(500).withReputation(standing(-25));

        ShopTransactionResult result = shopService.buy(hostile, shop, "smuggler");

        assertFalse(result.success(), "Buying a locked item should fail");
        assertTrue(result.message().contains("bandits"),
            "Rejection should name the faction: " + result.message());
        assertNull(result.updatedPlayer(), "A rejected purchase must yield no updated player");
        assertEquals(500, hostile.getGold(), "Gold must be untouched on rejection");
        assertTrue(hostile.getInventory().isEmpty(), "No item may be granted on rejection");
    }

    @Test
    void buy_succeeds_whenStandingMeetsThreshold() {
        Player neutral = playerWithGold(500).withReputation(standing(0));

        ShopTransactionResult result = shopService.buy(neutral, shop, "smuggler");

        assertTrue(result.success(), result.message());
        assertEquals(460, result.updatedPlayer().getGold(), "Should pay the base 40 gold");
        assertEquals(1, result.updatedPlayer().getInventory().size());
    }

    @Test
    void buy_ungatedEntry_alwaysSucceeds_regardlessOfStanding() {
        Player hostile = playerWithGold(500).withReputation(standing(-25));

        ShopTransactionResult result = shopService.buy(hostile, shop, "torch");

        assertTrue(result.success(), result.message());
        assertEquals(495, result.updatedPlayer().getGold(), "Ungated torch costs its base 5 gold");
    }

    private static PlayerReputation standing(int value) {
        return PlayerReputation.empty().adjust(BANDITS, value);
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
