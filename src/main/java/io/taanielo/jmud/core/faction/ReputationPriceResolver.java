package io.taanielo.jmud.core.faction;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Resolver that adjusts a shop's buy and sell prices for a player based on their reputation with the
 * shop's faction. Kept as a narrow port so {@code ShopService} depends only on this contract rather
 * than the full reputation domain, and so shops with no faction (or when reputation is disabled) can
 * fall back to base prices.
 */
public interface ReputationPriceResolver {

    /**
     * Resolves the price a player pays to buy an item.
     *
     * @param basePrice the shop's un-adjusted price
     * @param player    the buying player, whose reputation drives the adjustment
     * @param factionId the shop's faction, or {@code null} for a faction-neutral shop
     * @return the adjusted (never negative) buy price
     */
    int buyPrice(int basePrice, Player player, @Nullable FactionId factionId);

    /**
     * Resolves the payout a player receives to sell an item.
     *
     * @param baseValue the un-adjusted sell payout
     * @param player    the selling player, whose reputation drives the adjustment
     * @param factionId the shop's faction, or {@code null} for a faction-neutral shop
     * @return the adjusted (never negative) sell payout
     */
    int sellValue(int baseValue, Player player, @Nullable FactionId factionId);
}
