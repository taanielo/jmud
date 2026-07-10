package io.taanielo.jmud.core.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link AuctionExpiryTicker}, focusing on the expiry-return path (item returned to
 * the seller plus a MAIL notification) without any networking or file I/O.
 */
class AuctionExpiryTickerTest {

    private static final RoomId AUCTION_ROOM = RoomId.of("courtyard");
    private static final AuctionHouse HOUSE =
        new AuctionHouse("town-auction-house", "Mirela", AUCTION_ROOM);

    private StubListingRepository listings;

    @BeforeEach
    void setUp() {
        listings = new StubListingRepository();
    }

    @Test
    void tick_returnsExpiredItemAndMailsSeller() {
        AuctionService service = new AuctionService(new StubHouseRepository(), listings);
        long tick = 2100L;
        listings.stored.add(new AuctionListing(
            Username.of("alice"), sword(), 100, AUCTION_ROOM, 0L, tick));

        Player seller = playerNamed("alice");
        List<Player> persisted = new ArrayList<>();
        AuctionExpiryTicker ticker = new AuctionExpiryTicker(
            service,
            () -> tick,
            username -> Optional.of(seller),
            persisted::add);

        ticker.tick();

        assertEquals(1, persisted.size(), "the seller should be persisted once");
        Player updated = persisted.get(0);
        assertEquals(1, updated.getInventory().size(), "expired item should be returned");
        assertEquals(1, updated.mailbox().messages().size(), "seller should be mailed");
        assertTrue(updated.mailbox().messages().get(0).body().contains("expired"));
        assertTrue(listings.stored.isEmpty(), "expired listing should be removed");
    }

    @Test
    void tick_doesNothing_whenNoListingsExpired() {
        AuctionService service = new AuctionService(new StubHouseRepository(), listings);
        listings.stored.add(new AuctionListing(
            Username.of("alice"), sword(), 100, AUCTION_ROOM, 0L, 2100L));

        List<Player> persisted = new ArrayList<>();
        AuctionExpiryTicker ticker = new AuctionExpiryTicker(
            service,
            () -> 500L,
            username -> Optional.of(playerNamed("alice")),
            persisted::add);

        ticker.tick();

        assertTrue(persisted.isEmpty(), "nothing should be persisted while listings are active");
        assertEquals(1, listings.stored.size());
    }

    @Test
    void tick_skipsListing_whenSellerCannotBeResolved() {
        AuctionService service = new AuctionService(new StubHouseRepository(), listings);
        listings.stored.add(new AuctionListing(
            Username.of("ghost"), sword(), 100, AUCTION_ROOM, 0L, 10L));

        List<Player> persisted = new ArrayList<>();
        AuctionExpiryTicker ticker = new AuctionExpiryTicker(
            service,
            () -> 20L,
            username -> Optional.empty(),
            persisted::add);

        ticker.tick();

        assertTrue(persisted.isEmpty(), "an unresolvable seller cannot be credited");
        // The listing was still removed from storage by expireListings.
        assertTrue(listings.stored.isEmpty());
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static Item sword() {
        return Item.builder(ItemId.of("iron-sword"), "Iron Sword", "A plain iron sword.", ItemAttributes.empty())
            .value(25)
            .build();
    }

    private static Player playerNamed(String name) {
        User user = User.of(Username.of(name), Password.hash("pass", 1));
        return Player.of(user, "{hp}hp>");
    }

    private record StubHouseRepository() implements AuctionHouseRepository {
        @Override
        public List<AuctionHouse> findAll() {
            return List.of(HOUSE);
        }

        @Override
        public Optional<AuctionHouse> findByRoomId(RoomId roomId) {
            return HOUSE.roomId().equals(roomId) ? Optional.of(HOUSE) : Optional.empty();
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
