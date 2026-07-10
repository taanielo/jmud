package io.taanielo.jmud.core.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.ReputationPriceResolver;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Application service for shop interactions: listing stock, buying, and selling items.
 *
 * <p>All operations are stateless with respect to the shop; the {@link Player}
 * passed in is never mutated. Callers receive an updated {@link Player} in the
 * returned {@link ShopTransactionResult} on success.
 */
@Slf4j
public class ShopService {

    private final ShopRepository shopRepository;
    private final ItemRepository itemRepository;
    /** Optional resolver that shifts prices by the player's faction reputation; {@code null} disables it. */
    @Nullable
    private final ReputationPriceResolver priceResolver;

    public ShopService(ShopRepository shopRepository, ItemRepository itemRepository) {
        this(shopRepository, itemRepository, null);
    }

    /**
     * Creates a shop service whose prices are adjusted by the given reputation resolver.
     *
     * @param shopRepository the shop repository
     * @param itemRepository the item repository
     * @param priceResolver  resolver applying reputation-based price shifts, or {@code null} to
     *                       always use base prices
     */
    public ShopService(
        ShopRepository shopRepository,
        ItemRepository itemRepository,
        @Nullable ReputationPriceResolver priceResolver
    ) {
        this.shopRepository = Objects.requireNonNull(shopRepository, "shopRepository is required");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository is required");
        this.priceResolver = priceResolver;
    }

    /**
     * Applies the shop's faction reputation adjustment (if any) to a buy price.
     *
     * @param basePrice the un-adjusted price
     * @param player    the buying player
     * @param shop      the shop being visited
     * @return the reputation-adjusted buy price
     */
    private int adjustedBuyPrice(int basePrice, Player player, Shop shop) {
        return priceResolver == null
            ? basePrice
            : priceResolver.buyPrice(basePrice, player, shop.factionId());
    }

    /**
     * Applies the shop's faction reputation adjustment (if any) to a sell payout.
     *
     * @param baseValue the un-adjusted payout
     * @param player    the selling player
     * @param shop      the shop being visited
     * @return the reputation-adjusted sell payout
     */
    private int adjustedSellValue(int baseValue, Player player, Shop shop) {
        return priceResolver == null
            ? baseValue
            : priceResolver.sellValue(baseValue, player, shop.factionId());
    }

    /**
     * Returns whether the given stock entry is locked for the player by a reputation gate: it carries
     * a {@code minReputation} threshold and the player's standing with the shop's faction is strictly
     * below it. Entries with no gate, or shops with no faction, are never locked, so ungated stock and
     * faction-neutral shops behave exactly as before.
     *
     * @param entry  the stock entry to test
     * @param player the viewing/buying player
     * @param shop   the shop being visited
     * @return {@code true} when the player cannot yet access this entry
     */
    private boolean isLocked(StockEntry entry, Player player, Shop shop) {
        Integer minReputation = entry.minReputation();
        FactionId factionId = shop.factionId();
        if (minReputation == null || factionId == null) {
            return false;
        }
        return player.reputation().standing(factionId) < minReputation;
    }

    /**
     * Returns the shop located in the given room, if any.
     */
    public Optional<Shop> findShopInRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId is required");
        try {
            return shopRepository.findByRoomId(roomId);
        } catch (ShopRepositoryException e) {
            log.warn("Failed to look up shop in room {}: {}", roomId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Produces a formatted listing of the shop's available stock.
     *
     * @param shop the shop to list
     * @return lines ready to be sent to the player's connection
     */
    public List<String> formatListing(Shop shop) {
        return formatListing(shop, null);
    }

    /**
     * Produces a formatted listing of the shop's stock with prices adjusted for the given player's
     * reputation with the shop's faction.
     *
     * @param shop   the shop to list
     * @param player the viewing player, or {@code null} to show un-adjusted base prices
     * @return lines ready to be sent to the player's connection
     */
    public List<String> formatListing(Shop shop, @Nullable Player player) {
        Objects.requireNonNull(shop, "shop is required");
        List<String> lines = new ArrayList<>();
        lines.add(shop.name() + " — wares for sale:");
        lines.add(String.format("  %-24s %-30s %-10s %s", "Item", "Description", "Price", "Sell"));
        lines.add("  " + "-".repeat(74));
        for (StockEntry entry : shop.stock()) {
            try {
                Optional<Item> itemOpt = itemRepository.findById(entry.itemId());
                if (itemOpt.isEmpty()) {
                    log.warn("Shop {} references unknown item {}", shop.id(), entry.itemId());
                    continue;
                }
                Item item = itemOpt.get();
                String desc = item.getDescription();
                if (desc.length() > 29) {
                    desc = desc.substring(0, 26) + "...";
                }
                if (player != null && isLocked(entry, player, shop)) {
                    lines.add(String.format("  %-24s %-30s %s",
                        item.getName(), desc,
                        "[locked — requires better standing with " + shop.factionId().getValue() + "]"));
                    continue;
                }
                int price = entry.price() != null ? entry.price() : item.getValue();
                int sellValue = (int) Math.floor(item.getValue() * shop.sellRatio());
                if (player != null) {
                    price = adjustedBuyPrice(price, player, shop);
                    sellValue = adjustedSellValue(sellValue, player, shop);
                }
                lines.add(String.format("  %-24s %-30s %-10s %d gold",
                    item.getName(), desc, price + " gold", sellValue));
            } catch (RepositoryException e) {
                log.warn("Failed to load item {} for shop listing: {}", entry.itemId(), e.getMessage());
            }
        }
        return lines;
    }

    /**
     * Attempts to buy the named item from the shop on behalf of the player.
     *
     * @param player    the buying player
     * @param shop      the shop being visited
     * @param itemInput the name (or prefix) of the item to buy
     * @return a result describing success or failure
     */
    public ShopTransactionResult buy(Player player, Shop shop, String itemInput) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(shop, "shop is required");
        if (itemInput == null || itemInput.isBlank()) {
            return ShopTransactionResult.failure("Buy what?");
        }
        String normalized = itemInput.trim().toLowerCase(Locale.ROOT);

        // Find the stock entry matching the input.
        StockEntry entry = null;
        Item item = null;
        for (StockEntry e : shop.stock()) {
            try {
                Optional<Item> itemOpt = itemRepository.findById(e.itemId());
                if (itemOpt.isEmpty()) {
                    continue;
                }
                Item candidate = itemOpt.get();
                String name = candidate.getName().toLowerCase(Locale.ROOT);
                if (name.equals(normalized) || name.startsWith(normalized)) {
                    entry = e;
                    item = candidate;
                    break;
                }
            } catch (RepositoryException ex) {
                log.warn("Failed to load item {} during buy lookup: {}", e.itemId(), ex.getMessage());
            }
        }

        if (entry == null || item == null) {
            return ShopTransactionResult.failure(
                "The shop does not carry '" + itemInput.trim() + "'.");
        }

        if (isLocked(entry, player, shop)) {
            return ShopTransactionResult.failure(
                "The " + item.getName() + " is not for sale to you. It requires better standing with "
                    + shop.factionId().getValue() + " (at least " + entry.minReputation() + ").");
        }

        int price = adjustedBuyPrice(entry.price() != null ? entry.price() : item.getValue(), player, shop);
        if (player.getGold() < price) {
            return ShopTransactionResult.failure(
                "You cannot afford that. It costs " + price + " gold and you only have "
                    + player.getGold() + " gold.");
        }

        Player updated = player.addGold(-price).addItem(item);
        return ShopTransactionResult.success(
            "You buy a " + item.getName() + " for " + price + " gold. "
                + "You now have " + updated.getGold() + " gold.",
            updated
        );
    }

    /**
     * Attempts to sell the named item from the player's inventory to the shop.
     *
     * <p>The shop pays {@code floor(item.value * shop.sellRatio)} gold, minimum 0.
     *
     * @param player    the selling player
     * @param shop      the shop being visited
     * @param itemInput the name (or prefix) of the item to sell
     * @return a result describing success or failure
     */
    public ShopTransactionResult sell(Player player, Shop shop, String itemInput) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(shop, "shop is required");
        if (itemInput == null || itemInput.isBlank()) {
            return ShopTransactionResult.failure("Sell what?");
        }
        String normalized = itemInput.trim().toLowerCase(Locale.ROOT);

        Item found = null;
        for (Item invItem : player.getInventory()) {
            String name = invItem.getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                found = invItem;
                break;
            }
            String id = invItem.getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                found = invItem;
                break;
            }
        }

        if (found == null) {
            return ShopTransactionResult.failure(
                "You are not carrying '" + itemInput.trim() + "'.");
        }

        int earned = adjustedSellValue((int) Math.floor(found.getValue() * shop.sellRatio()), player, shop);
        Player updated = player.removeItem(found).addGold(earned);
        return ShopTransactionResult.success(
            "You sell the " + found.getName() + " for " + earned + " gold. "
                + "You now have " + updated.getGold() + " gold.",
            updated
        );
    }
}
