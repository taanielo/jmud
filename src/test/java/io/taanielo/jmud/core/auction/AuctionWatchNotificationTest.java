package io.taanielo.jmud.core.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerAuctionWatchList;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Verifies the {@code AUCTION WATCH} notification-selection rule without networking: given a real
 * listing produced by {@link AuctionService#sell}, exactly the online players (other than the seller)
 * whose watch list matches the item's name via {@link AuctionFilter} keyword semantics are selected.
 * This mirrors the scan performed in {@code SocketCommandContextImpl#notifyAuctionWatchers}.
 */
class AuctionWatchNotificationTest {

    private static final RoomId AUCTION_ROOM = RoomId.of("courtyard");
    private static final AuctionHouse HOUSE =
        new AuctionHouse("town-auction-house", "Mirela the Auctioneer", AUCTION_ROOM);
    private static final long CREATED_TICK = 100L;
    private static final long EXPIRY_TICK = 2100L;

    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionService(new StubHouseRepository(HOUSE), new StubListingRepository());
    }

    @Test
    void matchingWatcherIsSelected_sellerAndNonMatchingWatcherAreNot() {
        Player seller = playerNamed("alice").addItem(sword());
        AuctionTransactionResult result = auctionService.sell(
            seller, "iron sword", 100, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK);
        assertTrue(result.success());
        AuctionListing listing = result.listing();
        assertNotNull(listing);

        Player matchingWatcher = playerNamed("bob")
            .withAuctionWatchList(new PlayerAuctionWatchList(List.of("sword")));
        Player nonMatchingWatcher = playerNamed("carol")
            .withAuctionWatchList(new PlayerAuctionWatchList(List.of("potion")));
        // The seller is also watching a matching keyword but must never be notified of their own listing.
        Player sellerWatching = playerNamed("alice")
            .withAuctionWatchList(new PlayerAuctionWatchList(List.of("sword")));

        List<Player> online = List.of(matchingWatcher, nonMatchingWatcher, sellerWatching);

        List<String> notified = selectNotified(online, listing, Username.of("alice"));

        assertEquals(List.of("bob"), notified);
    }

    @Test
    void caseInsensitiveSubstringMatchAgreesWithAuctionListFilter() {
        Player seller = playerNamed("alice").addItem(
            namedItem("flaming-blade", "Flaming Blade of Doom"));
        AuctionListing listing = auctionService.sell(
            seller, "flaming", 100, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK).listing();
        assertNotNull(listing);

        Player watcher = playerNamed("bob")
            .withAuctionWatchList(new PlayerAuctionWatchList(List.of("BLADE")));

        List<String> notified = selectNotified(List.of(watcher), listing, Username.of("alice"));
        assertEquals(List.of("bob"), notified);

        // The very same keyword under AUCTION LIST must surface the same listing.
        assertEquals(1, auctionService.activeListings(CREATED_TICK, AuctionFilter.keyword("blade")).size());
    }

    /** Mirror of the adapter's watch scan: pick online, non-seller players whose watch list matches. */
    private static List<String> selectNotified(List<Player> online, AuctionListing listing, Username seller) {
        List<String> notified = new ArrayList<>();
        for (Player watcher : online) {
            if (watcher.getUsername().equals(seller)) {
                continue;
            }
            for (String keyword : watcher.auctionWatchList().keywords()) {
                if (AuctionFilter.keyword(keyword).matches(listing)) {
                    notified.add(watcher.getUsername().getValue());
                    break;
                }
            }
        }
        return notified;
    }

    private static Item sword() {
        return Item.builder(ItemId.of("iron-sword"), "Iron Sword", "A plain iron sword.", ItemAttributes.empty())
            .value(25)
            .build();
    }

    private static Item namedItem(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A " + name + ".", ItemAttributes.empty())
            .value(25)
            .build();
    }

    private static Player playerNamed(String name) {
        User user = User.of(Username.of(name), Password.hash("pass", 1));
        return Player.of(user, "{hp}hp>");
    }

    private record StubHouseRepository(AuctionHouse house) implements AuctionHouseRepository {
        @Override
        public List<AuctionHouse> findAll() {
            return List.of(house);
        }

        @Override
        public Optional<AuctionHouse> findByRoomId(RoomId roomId) {
            return house.roomId().equals(roomId) ? Optional.of(house) : Optional.empty();
        }
    }

    private static final class StubListingRepository implements AuctionRepository {
        private final List<AuctionListing> stored = new ArrayList<>();

        @Override
        public List<AuctionListing> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public void save(List<AuctionListing> newListings) {
            stored.clear();
            stored.addAll(newListings);
        }
    }
}
