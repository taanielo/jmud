package io.taanielo.jmud.core.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * Unit tests for {@link AuctionService}, run without networking or file I/O — both repositories are
 * stubbed in-memory.
 */
class AuctionServiceTest {

    private static final RoomId AUCTION_ROOM = RoomId.of("courtyard");
    private static final RoomId OTHER_ROOM = RoomId.of("training-yard");
    private static final AuctionHouse HOUSE =
        new AuctionHouse("town-auction-house", "Mirela the Auctioneer", AUCTION_ROOM);
    private static final long CREATED_TICK = 100L;
    private static final long EXPIRY_TICK = 2100L;

    private StubListingRepository listings;
    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        listings = new StubListingRepository();
        auctionService = new AuctionService(new StubHouseRepository(HOUSE), listings);
    }

    // ── findAuctionHouseInRoom ─────────────────────────────────────────

    @Test
    void findAuctionHouseInRoom_returnsHouse_whenPresent() {
        Optional<AuctionHouse> result = auctionService.findAuctionHouseInRoom(AUCTION_ROOM);
        assertTrue(result.isPresent());
        assertEquals("Mirela the Auctioneer", result.get().auctioneer());
    }

    @Test
    void findAuctionHouseInRoom_returnsEmpty_whenAbsent() {
        assertTrue(auctionService.findAuctionHouseInRoom(OTHER_ROOM).isEmpty());
    }

    // ── sell ───────────────────────────────────────────────────────────

    @Test
    void sell_success_removesItemAndCreatesListing() {
        Player seller = playerNamed("alice").addItem(sword());
        AuctionTransactionResult result = auctionService.sell(
            seller, "iron sword", 100, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK);

        assertTrue(result.success());
        assertNotNull(result.updatedActor());
        assertTrue(result.updatedActor().getInventory().isEmpty(), "item should leave the seller's inventory");
        assertEquals(1, auctionService.activeListings(CREATED_TICK).size());
        assertEquals(100, auctionService.activeListings(CREATED_TICK).get(0).price());
    }

    @Test
    void sell_failure_itemNotCarried() {
        Player seller = playerNamed("alice");
        AuctionTransactionResult result = auctionService.sell(
            seller, "iron sword", 100, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK);

        assertFalse(result.success());
        assertNull(result.updatedActor());
        assertTrue(auctionService.activeListings(CREATED_TICK).isEmpty());
    }

    @Test
    void sell_failure_nonPositivePrice() {
        Player seller = playerNamed("alice").addItem(sword());
        AuctionTransactionResult result = auctionService.sell(
            seller, "iron sword", 0, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK);

        assertFalse(result.success());
        assertNull(result.updatedActor());
    }

    // ── buy ────────────────────────────────────────────────────────────

    @Test
    void buy_success_deductsGoldAddsItemAndRemovesListing() {
        listWord();
        Player buyer = playerNamed("bob").withGold(500);
        AuctionTransactionResult result = auctionService.buy(buyer, 1, CREATED_TICK);

        assertTrue(result.success());
        assertNotNull(result.updatedActor());
        assertEquals(400, result.updatedActor().getGold(), "buyer should pay the price");
        assertEquals(1, result.updatedActor().getInventory().size(), "buyer should receive the item");
        assertNotNull(result.listing());
        assertEquals("alice", result.listing().seller().getValue());
        assertTrue(auctionService.activeListings(CREATED_TICK).isEmpty(), "listing should be removed");
    }

    @Test
    void buy_failure_ownListing() {
        listWord();
        Player alice = playerNamed("alice").withGold(500);
        AuctionTransactionResult result = auctionService.buy(alice, 1, CREATED_TICK);

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase(Locale.ROOT).contains("own"));
        assertEquals(1, auctionService.activeListings(CREATED_TICK).size());
    }

    @Test
    void buy_failure_insufficientGold() {
        listWord();
        Player buyer = playerNamed("bob").withGold(50);
        AuctionTransactionResult result = auctionService.buy(buyer, 1, CREATED_TICK);

        assertFalse(result.success());
        assertEquals(1, auctionService.activeListings(CREATED_TICK).size());
    }

    @Test
    void buy_failure_invalidNumber() {
        Player buyer = playerNamed("bob").withGold(500);
        AuctionTransactionResult result = auctionService.buy(buyer, 5, CREATED_TICK);
        assertFalse(result.success());
    }

    // ── cancel ─────────────────────────────────────────────────────────

    @Test
    void cancel_success_returnsItem() {
        listWord();
        Player alice = playerNamed("alice");
        AuctionTransactionResult result = auctionService.cancel(alice, 1, CREATED_TICK);

        assertTrue(result.success());
        assertNotNull(result.updatedActor());
        assertEquals(1, result.updatedActor().getInventory().size(), "item should return to the seller");
        assertTrue(auctionService.activeListings(CREATED_TICK).isEmpty());
    }

    @Test
    void cancel_failure_notOwnListing() {
        listWord();
        Player bob = playerNamed("bob");
        AuctionTransactionResult result = auctionService.cancel(bob, 1, CREATED_TICK);

        assertFalse(result.success());
        assertEquals(1, auctionService.activeListings(CREATED_TICK).size());
    }

    // ── filtered / sorted listing views ───────────────────────────────

    @Test
    void activeListings_noFilter_sortedByPriceAscending() {
        addListing("alice", namedItem("plate-mail", "Plate Mail"), 300);
        addListing("bob", namedItem("iron-sword", "Iron Sword"), 100);
        addListing("carol", namedItem("oak-staff", "Oak Staff"), 200);

        List<AuctionService.NumberedListing> view =
            auctionService.activeListings(CREATED_TICK, AuctionFilter.all());

        assertEquals(3, view.size());
        assertEquals(100, view.get(0).listing().price());
        assertEquals(200, view.get(1).listing().price());
        assertEquals(300, view.get(2).listing().price());
        // Numbers reflect insertion order (full active list), not the sorted position.
        assertEquals(2, view.get(0).number(), "cheapest was inserted second");
        assertEquals(3, view.get(1).number());
        assertEquals(1, view.get(2).number());
    }

    @Test
    void activeListings_keywordFilter_matchesCaseInsensitiveSubstring() {
        addListing("alice", namedItem("iron-sword", "Iron Sword"), 100);
        addListing("bob", namedItem("oak-staff", "Oak Staff"), 200);
        addListing("carol", namedItem("short-sword", "Short Sword"), 50);

        List<AuctionService.NumberedListing> view =
            auctionService.activeListings(CREATED_TICK, AuctionFilter.keyword("SWORD"));

        assertEquals(2, view.size());
        assertEquals(50, view.get(0).listing().price(), "still price-ascending within the filter");
        assertEquals(3, view.get(0).number());
        assertEquals(1, view.get(1).number());
    }

    @Test
    void activeListings_keywordFilter_noMatch_returnsEmpty() {
        addListing("alice", namedItem("iron-sword", "Iron Sword"), 100);

        assertTrue(auctionService.activeListings(CREATED_TICK, AuctionFilter.keyword("potion")).isEmpty());
    }

    @Test
    void activeListings_mineFilter_returnsOnlyCallersListings() {
        addListing("alice", namedItem("iron-sword", "Iron Sword"), 100);
        addListing("bob", namedItem("oak-staff", "Oak Staff"), 200);
        addListing("alice", namedItem("plate-mail", "Plate Mail"), 300);

        List<AuctionService.NumberedListing> view =
            auctionService.activeListings(CREATED_TICK, AuctionFilter.mine(Username.of("alice")));

        assertEquals(2, view.size());
        assertEquals("alice", view.get(0).listing().seller().getValue());
        assertEquals("alice", view.get(1).listing().seller().getValue());
        assertEquals(1, view.get(0).number());
        assertEquals(3, view.get(1).number());
    }

    @Test
    void numberingContract_buyByDisplayedNumberInFilteredView_buysCorrectItem() {
        addListing("alice", namedItem("iron-sword", "Iron Sword"), 100);
        addListing("bob", namedItem("oak-staff", "Oak Staff"), 200);
        addListing("carol", namedItem("short-sword", "Short Sword"), 50);

        // The buyer filters to swords; the cheapest displayed row is the Short Sword with number 3.
        List<AuctionService.NumberedListing> view =
            auctionService.activeListings(CREATED_TICK, AuctionFilter.keyword("sword"));
        AuctionService.NumberedListing cheapest = view.get(0);
        assertEquals("Short Sword", cheapest.listing().item().getName());

        Player buyer = playerNamed("dave").withGold(500);
        AuctionTransactionResult result = auctionService.buy(buyer, cheapest.number(), CREATED_TICK);

        assertTrue(result.success());
        assertNotNull(result.listing());
        assertEquals("Short Sword", result.listing().item().getName(),
            "BUY by displayed number must resolve to the same item shown in the filtered view");
    }

    // ── expiry ─────────────────────────────────────────────────────────

    @Test
    void activeListings_excludesExpired() {
        listWord();
        assertEquals(1, auctionService.activeListings(EXPIRY_TICK - 1).size());
        assertTrue(auctionService.activeListings(EXPIRY_TICK).isEmpty(), "expired at/after expiry tick");
    }

    @Test
    void expireListings_removesAndReturnsExpired() {
        listWord();
        List<AuctionListing> expired = auctionService.expireListings(EXPIRY_TICK);
        assertEquals(1, expired.size());
        assertEquals("alice", expired.get(0).seller().getValue());
        assertTrue(listings.stored.isEmpty(), "expired listing should be removed from storage");
    }

    @Test
    void expireListings_keepsActive() {
        listWord();
        assertTrue(auctionService.expireListings(EXPIRY_TICK - 1).isEmpty());
        assertEquals(1, listings.stored.size());
    }

    // ── seller-side application ────────────────────────────────────────

    @Test
    void applySaleCredit_addsGoldAndMail() {
        AuctionListing listing = new AuctionListing(
            Username.of("alice"), sword(), 100, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK);
        Player seller = playerNamed("alice").withGold(20);

        Player credited = auctionService.applySaleCredit(seller, listing, CREATED_TICK);

        assertEquals(120, credited.getGold());
        assertEquals(1, credited.mailbox().messages().size(), "seller should get a sale notification");
        assertTrue(credited.mailbox().messages().get(0).body().contains("sold"));
    }

    @Test
    void applyExpiredReturn_returnsItemAndMail() {
        AuctionListing listing = new AuctionListing(
            Username.of("alice"), sword(), 100, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK);
        Player seller = playerNamed("alice");

        Player returned = auctionService.applyExpiredReturn(seller, listing, CREATED_TICK);

        assertEquals(1, returned.getInventory().size(), "item should return to the seller");
        assertEquals(1, returned.mailbox().messages().size());
        assertTrue(returned.mailbox().messages().get(0).body().contains("expired"));
    }

    // ── gold conservation ──────────────────────────────────────────────

    @Test
    void buyThenCredit_conservesGold() {
        listWord();
        Player buyer = playerNamed("bob").withGold(500);
        Player seller = playerNamed("alice").withGold(0);
        int totalBefore = buyer.getGold() + seller.getGold();

        AuctionTransactionResult buy = auctionService.buy(buyer, 1, CREATED_TICK);
        assertTrue(buy.success());
        assertNotNull(buy.listing());
        Player creditedSeller = auctionService.applySaleCredit(seller, buy.listing(), CREATED_TICK);

        int totalAfter = buy.updatedActor().getGold() + creditedSeller.getGold();
        assertEquals(totalBefore, totalAfter, "no gold should be created or destroyed by an auction sale");
    }

    // ── helpers ────────────────────────────────────────────────────────

    /** Lists a sword by 'alice' directly into storage so buy/cancel tests start from a known state. */
    private void listWord() {
        listings.stored.add(new AuctionListing(
            Username.of("alice"), sword(), 100, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK));
    }

    /** Lists an item by the given seller at the given price directly into storage. */
    private void addListing(String seller, Item item, int price) {
        listings.stored.add(new AuctionListing(
            Username.of(seller), item, price, AUCTION_ROOM, CREATED_TICK, EXPIRY_TICK));
    }

    private static Item namedItem(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A " + name + ".", ItemAttributes.empty())
            .value(25)
            .build();
    }

    private static Item sword() {
        return Item.builder(ItemId.of("iron-sword"), "Iron Sword", "A plain iron sword.", ItemAttributes.empty())
            .value(25)
            .build();
    }

    private static Player playerNamed(String name) {
        User user = User.of(Username.of(name), Password.hash("pass", 1));
        return Player.of(user, "{hp}hp>");
    }

    // ── stub repositories ──────────────────────────────────────────────

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
