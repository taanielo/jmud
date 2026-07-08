package io.taanielo.jmud.core.world;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A data-driven stat affix: a named bundle of stat bonuses that may be attached to an {@link Item}
 * to lift it above its base {@link ItemAttributes}. Definitions are loaded from
 * {@code data/item-affixes.json} and resolved onto items by {@link ItemAffixService}.
 *
 * @param id               the unique affix identifier referenced by items
 * @param label            the human-readable label (e.g. {@code "of the Bear"}) for display and tooling
 * @param stats            the additive stat bonuses granted by this affix, keyed by stat name
 * @param allowedRarities  the rarity tiers on which this affix is permitted to roll; never empty
 */
public record ItemAffix(AffixId id, String label, Map<String, Integer> stats, Set<Rarity> allowedRarities) {

    public ItemAffix {
        Objects.requireNonNull(id, "Affix id is required");
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Affix label must not be blank");
        }
        Objects.requireNonNull(stats, "Affix stats are required");
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("Affix stat keys must not be blank");
            }
            Objects.requireNonNull(entry.getValue(), "Affix stat values must not be null");
        }
        stats = Map.copyOf(stats);
        Objects.requireNonNull(allowedRarities, "Affix allowed rarities are required");
        if (allowedRarities.isEmpty()) {
            throw new IllegalArgumentException("Affix must allow at least one rarity tier");
        }
        allowedRarities = Set.copyOf(EnumSet.copyOf(allowedRarities));
    }

    /**
     * Returns whether this affix is permitted to roll on the given rarity tier.
     *
     * @param rarity the rarity tier to test
     * @return {@code true} when the tier is in {@link #allowedRarities()}
     */
    public boolean allowsRarity(Rarity rarity) {
        return allowedRarities.contains(rarity);
    }
}
