package io.taanielo.jmud.core.auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.MailResult;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerMailService;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Application service implementing the Auction House rules: listing an item for sale, buying a
 * listing, cancelling one's own listing, and expiring stale listings.
 *
 * <p>All operations are pure with respect to the invoking {@link Player}: the player passed in is
 * never mutated in place; callers receive an updated {@link Player} in the returned
 * {@link AuctionTransactionResult}. The persistent set of listings is read and rewritten through the
 * injected {@link AuctionRepository}. Crediting an offline seller's gold and delivering mail is left
 * to the caller via {@link #applySaleCredit} / {@link #applyExpiredReturn}, so the same cross-player
 * update path used by {@code MAIL} can persist those changes.
 *
 * <p>Gold moves one-for-one from buyer to seller — no gold is ever created or destroyed, mirroring
 * the invariant documented on {@link io.taanielo.jmud.core.bank.BankService}.
 */
@Slf4j
public class AuctionService {

    private static final String NOTIFIER = "Auction House";

    private final AuctionHouseRepository houseRepository;
    private final AuctionRepository listingRepository;
    private final PlayerMailService mailService;

    /**
     * Creates an auction service.
     *
     * @param houseRepository   source of static Auction House definitions
     * @param listingRepository store of active listings
     */
    public AuctionService(AuctionHouseRepository houseRepository, AuctionRepository listingRepository) {
        this.houseRepository = Objects.requireNonNull(houseRepository, "houseRepository is required");
        this.listingRepository = Objects.requireNonNull(listingRepository, "listingRepository is required");
        this.mailService = new PlayerMailService();
    }

    /**
     * Returns the Auction House located in the given room, if any.
     *
     * @param roomId the room to search
     * @return the Auction House in the room, or empty
     */
    public Optional<AuctionHouse> findAuctionHouseInRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId is required");
        try {
            return houseRepository.findByRoomId(roomId);
        } catch (AuctionRepositoryException e) {
            log.warn("Failed to look up auction house in room {}: {}", roomId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns all active (non-expired) listings, oldest first.
     *
     * @param currentTick the current game tick, used to filter out expired listings
     * @return the active listings in insertion order
     */
    public List<AuctionListing> activeListings(long currentTick) {
        List<AuctionListing> all = safeFindAll();
        List<AuctionListing> active = new ArrayList<>();
        for (AuctionListing listing : all) {
            if (!listing.isExpired(currentTick)) {
                active.add(listing);
            }
        }
        return List.copyOf(active);
    }

    /**
     * Returns the active listings matching {@code filter}, sorted by ascending price, each paired
     * with the one-based number it holds in the full server-wide active-listing ordering (the same
     * number {@code AUCTION BUY <#>} / {@code AUCTION CANCEL <#>} resolve against).
     *
     * <p>Filtering and re-sorting never renumber rows: numbers are assigned from
     * {@link #activeListings(long)} <em>before</em> the filter is applied, so displaying a subset or
     * a price-sorted view can never point a subsequent {@code BUY}/{@code CANCEL} at the wrong item.
     *
     * @param currentTick the current game tick, used to resolve the active listing ordering
     * @param filter      the view filter to apply
     * @return the surviving listings, price-ascending, with their original numbering preserved
     */
    public List<NumberedListing> activeListings(long currentTick, AuctionFilter filter) {
        Objects.requireNonNull(filter, "filter is required");
        List<AuctionListing> active = activeListings(currentTick);
        List<NumberedListing> filtered = new ArrayList<>();
        int number = 0;
        for (AuctionListing listing : active) {
            number++;
            if (filter.matches(listing)) {
                filtered.add(new NumberedListing(number, listing));
            }
        }
        filtered.sort(Comparator.comparingInt(entry -> entry.listing().price()));
        return List.copyOf(filtered);
    }

    /**
     * An active listing paired with its stable one-based number from the full active-listing order.
     *
     * @param number  the one-based number to display and to resolve {@code BUY}/{@code CANCEL} against
     * @param listing the listing itself
     */
    public record NumberedListing(int number, AuctionListing listing) {
        public NumberedListing {
            Objects.requireNonNull(listing, "listing is required");
        }
    }

    /**
     * Lists the named item from the seller's inventory for the given gold price, unequipping it
     * first if worn (matching {@code GIVE}). Fails without any state change when the price is not
     * positive or the item is not in the seller's inventory.
     *
     * @param seller     the listing player
     * @param itemInput  the item name or id to list
     * @param price      the asking price in gold; must be positive
     * @param roomId     the Auction House room the listing is created at
     * @param createdTick the tick the listing is created
     * @param expiryTick  the tick the listing expires
     * @return the result; on success the seller with the item removed and the created listing
     */
    public AuctionTransactionResult sell(
        Player seller, String itemInput, int price, RoomId roomId, long createdTick, long expiryTick) {
        Objects.requireNonNull(seller, "seller is required");
        Objects.requireNonNull(roomId, "roomId is required");
        if (price <= 0) {
            return AuctionTransactionResult.failure("You must set a positive gold price.");
        }
        String normalized = itemInput == null ? "" : itemInput.trim();
        if (normalized.isEmpty()) {
            return AuctionTransactionResult.failure("Sell what? Usage: AUCTION SELL <item> <price>");
        }
        Item item = matchItem(seller.getInventory(), normalized);
        if (item == null) {
            return AuctionTransactionResult.failure("You aren't carrying that.");
        }

        List<AuctionListing> all;
        try {
            all = new ArrayList<>(listingRepository.findAll());
        } catch (AuctionRepositoryException e) {
            log.warn("Failed to read auction listings for sell: {}", e.getMessage());
            return AuctionTransactionResult.failure("The Auction House ledger is unavailable right now.");
        }

        AuctionListing listing = new AuctionListing(
            seller.getUsername(), item, price, roomId, createdTick, expiryTick);
        all.add(listing);
        if (!trySave(all)) {
            return AuctionTransactionResult.failure("The Auction House ledger is unavailable right now.");
        }

        Player updatedSeller = removeFromInventory(seller, item);
        return AuctionTransactionResult.success(
            "You list " + item.getName() + " for " + price + " gold.", updatedSeller, listing);
    }

    /**
     * Buys the listing at the given one-based number (as shown by {@code AUCTION LIST}). Fails when
     * the number is invalid, the buyer would purchase their own listing, or the buyer cannot afford
     * the price. On success the buyer's gold is deducted and the item added to their inventory; the
     * listing is removed. The seller is credited separately by the caller via {@link #applySaleCredit}.
     *
     * @param buyer        the buying player
     * @param listingNumber the one-based listing number
     * @param currentTick  the current game tick, used to resolve the active listing ordering
     * @return the result; on success the buyer with gold/inventory updated and the bought listing
     */
    public AuctionTransactionResult buy(Player buyer, int listingNumber, long currentTick) {
        Objects.requireNonNull(buyer, "buyer is required");
        List<AuctionListing> active = activeListings(currentTick);
        AuctionListing listing = resolveByNumber(active, listingNumber);
        if (listing == null) {
            return AuctionTransactionResult.failure("There is no auction numbered " + listingNumber + ".");
        }
        if (listing.seller().equals(buyer.getUsername())) {
            return AuctionTransactionResult.failure("You cannot buy your own listing.");
        }
        if (buyer.getGold() < listing.price()) {
            return AuctionTransactionResult.failure(
                "You cannot afford that. It costs " + listing.price() + " gold and you have "
                    + buyer.getGold() + ".");
        }
        if (!removeListing(listing)) {
            return AuctionTransactionResult.failure("The Auction House ledger is unavailable right now.");
        }
        Player updatedBuyer = buyer.addGold(-listing.price()).addItem(listing.item());
        return AuctionTransactionResult.success(
            "You buy " + listing.item().getName() + " for " + listing.price() + " gold.",
            updatedBuyer, listing);
    }

    /**
     * Cancels the seller's own listing at the given one-based number, returning the item to their
     * inventory. Fails when the number is invalid or the listing belongs to another player.
     *
     * @param seller       the cancelling player
     * @param listingNumber the one-based listing number
     * @param currentTick  the current game tick, used to resolve the active listing ordering
     * @return the result; on success the seller with the item returned and the cancelled listing
     */
    public AuctionTransactionResult cancel(Player seller, int listingNumber, long currentTick) {
        Objects.requireNonNull(seller, "seller is required");
        List<AuctionListing> active = activeListings(currentTick);
        AuctionListing listing = resolveByNumber(active, listingNumber);
        if (listing == null) {
            return AuctionTransactionResult.failure("There is no auction numbered " + listingNumber + ".");
        }
        if (!listing.seller().equals(seller.getUsername())) {
            return AuctionTransactionResult.failure("That is not your listing.");
        }
        if (!removeListing(listing)) {
            return AuctionTransactionResult.failure("The Auction House ledger is unavailable right now.");
        }
        Player updatedSeller = seller.addItem(listing.item());
        return AuctionTransactionResult.success(
            "You cancel your listing of " + listing.item().getName() + ".", updatedSeller, listing);
    }

    /**
     * Removes every listing that has expired as of {@code currentTick}, persists the remainder, and
     * returns the removed listings so the caller can return each item to its seller. Called only from
     * the tick thread by {@link AuctionExpiryTicker}.
     *
     * @param currentTick the current game tick
     * @return the listings that expired on or before this tick (may be empty)
     */
    public List<AuctionListing> expireListings(long currentTick) {
        List<AuctionListing> all = safeFindAll();
        List<AuctionListing> remaining = new ArrayList<>();
        List<AuctionListing> expired = new ArrayList<>();
        for (AuctionListing listing : all) {
            if (listing.isExpired(currentTick)) {
                expired.add(listing);
            } else {
                remaining.add(listing);
            }
        }
        if (expired.isEmpty()) {
            return List.of();
        }
        if (!trySave(remaining)) {
            // Persistence failed; do not report expiries so items are not lost — retry next tick.
            return List.of();
        }
        return List.copyOf(expired);
    }

    /**
     * Credits a seller for a sold listing: adds the sale price to their gold and mails them a
     * notification. Pure — returns the updated seller; the caller persists it wherever the seller is.
     *
     * @param seller      the seller to credit (online or freshly loaded from persistence)
     * @param listing     the sold listing
     * @param currentTick the current game tick, used to time-stamp the mail
     * @return the seller with the price credited and a sale notification appended (mail is skipped
     *         silently when the mailbox is full)
     */
    public Player applySaleCredit(Player seller, AuctionListing listing, long currentTick) {
        Objects.requireNonNull(seller, "seller is required");
        Objects.requireNonNull(listing, "listing is required");
        Player credited = seller.addGold(listing.price());
        String body = "Your " + listing.item().getName() + " sold for " + listing.price() + " gold.";
        return withMail(credited, body, currentTick);
    }

    /**
     * Returns an expired listing's item to its seller and mails them a notification. Pure — returns
     * the updated seller; the caller persists it wherever the seller is.
     *
     * @param seller      the seller to return the item to (online or freshly loaded from persistence)
     * @param listing     the expired listing
     * @param currentTick the current game tick, used to time-stamp the mail
     * @return the seller with the item returned and an expiry notification appended (mail is skipped
     *         silently when the mailbox is full)
     */
    public Player applyExpiredReturn(Player seller, AuctionListing listing, long currentTick) {
        Objects.requireNonNull(seller, "seller is required");
        Objects.requireNonNull(listing, "listing is required");
        Player returned = seller.addItem(listing.item());
        String body = "Your listing for " + listing.item().getName() + " expired and was returned to you.";
        return withMail(returned, body, currentTick);
    }

    // ── private helpers ───────────────────────────────────────────────

    private Player withMail(Player player, String body, long currentTick) {
        MailResult result = mailService.send(player, NOTIFIER, currentTick, body);
        return result.success() && result.updatedPlayer() != null ? result.updatedPlayer() : player;
    }

    private @Nullable AuctionListing resolveByNumber(List<AuctionListing> active, int listingNumber) {
        int index = listingNumber - 1;
        if (index < 0 || index >= active.size()) {
            return null;
        }
        return active.get(index);
    }

    private boolean removeListing(AuctionListing listing) {
        List<AuctionListing> all = new ArrayList<>(safeFindAll());
        all.remove(listing);
        return trySave(all);
    }

    private List<AuctionListing> safeFindAll() {
        try {
            return listingRepository.findAll();
        } catch (AuctionRepositoryException e) {
            log.warn("Failed to read auction listings: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean trySave(List<AuctionListing> listings) {
        try {
            listingRepository.save(listings);
            return true;
        } catch (AuctionRepositoryException e) {
            log.warn("Failed to save auction listings: {}", e.getMessage());
            return false;
        }
    }

    private static Player removeFromInventory(Player player, Item item) {
        PlayerEquipment equipment = player.getEquipment();
        if (equipment.isEquipped(item.getId())) {
            EquipmentSlot slot = equipment.equippedSlot(item.getId());
            if (slot != null) {
                equipment = equipment.unequip(slot);
            }
        }
        return player.removeItem(item).withEquipment(equipment);
    }

    private static @Nullable Item matchItem(List<Item> items, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Item item : items) {
            String name = item.getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return item;
            }
            String id = item.getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return item;
            }
        }
        return null;
    }
}
