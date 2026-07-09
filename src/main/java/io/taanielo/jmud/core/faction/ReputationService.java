package io.taanielo.jmud.core.faction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Domain service governing faction reputation: it adjusts a player's standing when a faction's mob is
 * slain, decides whether a faction's mobs are hostile to a player, and (as a
 * {@link ReputationPriceResolver}) shifts faction-aligned shop prices by standing.
 *
 * <p>Faction definitions are snapshotted into an in-memory map at construction so every lookup is a
 * pure map read — no disk I/O ever runs on the tick thread (AGENTS.md §5). All calculations are
 * deterministic functions of the player's standing and the faction's configured constants, so results
 * are reproducible tick-to-tick.
 */
public final class ReputationService implements ReputationPriceResolver {

    /** Lower and upper clamps applied to a resolved price multiplier. */
    private static final double MIN_PRICE_MULTIPLIER = 0.5;
    private static final double MAX_PRICE_MULTIPLIER = 2.0;

    private final Map<FactionId, Faction> factions;

    /**
     * Creates a service over the given faction definitions, snapshotting them into memory.
     *
     * @param repository the faction repository to load definitions from
     * @throws FactionRepositoryException when the definitions cannot be loaded
     */
    public ReputationService(FactionRepository repository) throws FactionRepositoryException {
        Objects.requireNonNull(repository, "Faction repository is required");
        Map<FactionId, Faction> loaded = new HashMap<>();
        for (Faction faction : repository.findAll()) {
            loaded.put(faction.id(), faction);
        }
        this.factions = Map.copyOf(loaded);
    }

    /**
     * Returns the faction with the given id, if defined.
     *
     * @param factionId the faction to look up
     * @return the faction, or empty when unknown
     */
    public Optional<Faction> findFaction(FactionId factionId) {
        return Optional.ofNullable(factions.get(factionId));
    }

    /**
     * Returns a copy of the killer with their standing toward the given faction adjusted by that
     * faction's {@link Faction#killReputationDelta()}. When the faction is unknown the player is
     * returned unchanged.
     *
     * <p>Tick-thread only (AGENTS.md §5): called from the combat loop when a mob dies.
     *
     * @param killer    the player who landed the killing blow
     * @param factionId the slain mob's faction
     * @return the killer with updated reputation
     */
    public Player recordKill(Player killer, FactionId factionId) {
        Objects.requireNonNull(killer, "Killer is required");
        Objects.requireNonNull(factionId, "Faction id is required");
        Faction faction = factions.get(factionId);
        if (faction == null || faction.killReputationDelta() == 0) {
            return killer;
        }
        return killer.withReputation(killer.reputation().adjust(factionId, faction.killReputationDelta()));
    }

    /**
     * Returns whether a mob of the given faction should treat a player with the given reputation as
     * an enemy. A player is hostile when their standing is strictly below the faction's
     * {@link Faction#hostileThreshold()}. Unknown factions are never hostile.
     *
     * @param reputation the player's reputation
     * @param factionId  the mob's faction
     * @return {@code true} when the faction's mobs should engage this player
     */
    public boolean isHostile(PlayerReputation reputation, FactionId factionId) {
        Objects.requireNonNull(reputation, "Reputation is required");
        Objects.requireNonNull(factionId, "Faction id is required");
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        return reputation.standing(factionId) < faction.hostileThreshold();
    }

    @Override
    public int buyPrice(int basePrice, Player player, @Nullable FactionId factionId) {
        return adjust(basePrice, player, factionId, true);
    }

    @Override
    public int sellValue(int baseValue, Player player, @Nullable FactionId factionId) {
        return adjust(baseValue, player, factionId, false);
    }

    private int adjust(int base, Player player, @Nullable FactionId factionId, boolean buying) {
        Objects.requireNonNull(player, "Player is required");
        if (factionId == null) {
            return base;
        }
        Faction faction = factions.get(factionId);
        if (faction == null || faction.priceModifierPerPoint() == 0.0) {
            return base;
        }
        int standing = player.reputation().standing(factionId);
        // Friendly standing lowers buy prices and raises sell payouts; hostile standing does the
        // reverse. The sign flip between buying and selling keeps both directions favouring allies.
        double signedShift = (buying ? -1.0 : 1.0) * standing * faction.priceModifierPerPoint();
        double multiplier = clamp(1.0 + signedShift, MIN_PRICE_MULTIPLIER, MAX_PRICE_MULTIPLIER);
        return Math.max(0, (int) Math.round(base * multiplier));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
