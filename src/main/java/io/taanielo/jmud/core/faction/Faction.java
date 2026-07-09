package io.taanielo.jmud.core.faction;

import java.util.Objects;

/**
 * Immutable definition of a faction loaded from a {@code data/factions/*.json} file.
 *
 * <p>A faction bundles the reputation rules that govern how the world reacts to a player:
 * <ul>
 *   <li>{@link #killReputationDelta()} — the signed change applied to a player's standing with this
 *       faction each time one of its mobs is slain (typically negative: the faction resents the
 *       killer).</li>
 *   <li>{@link #hostileThreshold()} — a mob of this faction treats a player whose standing is
 *       <em>strictly below</em> this value as an enemy and will initiate combat.</li>
 *   <li>{@link #priceModifierPerPoint()} — how strongly a shop tied to this faction shifts its
 *       prices per point of standing (friendly standing lowers buy prices and raises sell payouts;
 *       hostile standing does the reverse).</li>
 * </ul>
 *
 * @param id                    unique faction id
 * @param name                  human-readable display name (e.g. {@code "the Bandit Brotherhood"})
 * @param description           short flavour description
 * @param killReputationDelta   standing change applied to a killer per slain faction mob
 * @param hostileThreshold      standing below which faction mobs are hostile
 * @param priceModifierPerPoint fractional price shift per standing point at faction-aligned shops
 */
public record Faction(
    FactionId id,
    String name,
    String description,
    int killReputationDelta,
    int hostileThreshold,
    double priceModifierPerPoint
) {
    public Faction {
        Objects.requireNonNull(id, "Faction id is required");
        Objects.requireNonNull(name, "Faction name is required");
        Objects.requireNonNull(description, "Faction description is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Faction name must not be blank");
        }
        if (priceModifierPerPoint < 0.0) {
            throw new IllegalArgumentException(
                "Faction priceModifierPerPoint must be non-negative, got " + priceModifierPerPoint);
        }
    }
}
