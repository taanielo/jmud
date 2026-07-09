package io.taanielo.jmud.core.shop;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Immutable definition of a shopkeeper NPC loaded from data files.
 *
 * <p>A shop is associated with a single room via {@link #roomId()}.
 * Its {@link #stock()} lists the items available for purchase and optional
 * per-item prices. {@link #sellRatio()} determines what fraction of an
 * item's base value the shop pays when a player sells an item.
 *
 * <p>An optional {@link #factionId()} ties the shop to a faction: prices then shift with the
 * buying/selling player's reputation with that faction (see the reputation system).
 */
public record Shop(
    ShopId id,
    String name,
    RoomId roomId,
    List<StockEntry> stock,
    double sellRatio,
    @Nullable FactionId factionId
) {
    public Shop {
        Objects.requireNonNull(id, "Shop id is required");
        Objects.requireNonNull(name, "Shop name is required");
        Objects.requireNonNull(roomId, "Shop roomId is required");
        Objects.requireNonNull(stock, "Shop stock is required");
        stock = List.copyOf(stock);
        if (sellRatio < 0.0 || sellRatio > 1.0) {
            throw new IllegalArgumentException(
                "sellRatio must be between 0.0 and 1.0, got " + sellRatio);
        }
    }

    /**
     * Convenience constructor for a faction-neutral shop (no reputation-based price adjustment).
     */
    public Shop(ShopId id, String name, RoomId roomId, List<StockEntry> stock, double sellRatio) {
        this(id, name, roomId, stock, sellRatio, null);
    }
}
