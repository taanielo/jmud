package io.taanielo.jmud.core.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
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

    public ShopService(ShopRepository shopRepository, ItemRepository itemRepository) {
        this.shopRepository = Objects.requireNonNull(shopRepository, "shopRepository is required");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository is required");
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
        Objects.requireNonNull(shop, "shop is required");
        List<String> lines = new ArrayList<>();
        lines.add(shop.name() + " — wares for sale:");
        lines.add(String.format("  %-24s %-30s %s", "Item", "Description", "Price"));
        lines.add("  " + "-".repeat(62));
        for (StockEntry entry : shop.stock()) {
            try {
                Optional<Item> itemOpt = itemRepository.findById(entry.itemId());
                if (itemOpt.isEmpty()) {
                    log.warn("Shop {} references unknown item {}", shop.id(), entry.itemId());
                    continue;
                }
                Item item = itemOpt.get();
                int price = entry.price() != null ? entry.price() : item.getValue();
                String desc = item.getDescription();
                if (desc.length() > 29) {
                    desc = desc.substring(0, 26) + "...";
                }
                lines.add(String.format("  %-24s %-30s %d gold", item.getName(), desc, price));
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

        int price = entry.price() != null ? entry.price() : item.getValue();
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

        int earned = (int) Math.floor(found.getValue() * shop.sellRatio());
        Player updated = player.removeItem(found).addGold(earned);
        return ShopTransactionResult.success(
            "You sell the " + found.getName() + " for " + earned + " gold. "
                + "You now have " + updated.getGold() + " gold.",
            updated
        );
    }
}
