package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Rarity facet of an {@link Item}: its {@link Rarity} tier and the ids of the stat affixes attached
 * to it. This is a construction-time value object grouping the rarity-related parameters so item
 * features can grow without reshaping {@link Item}'s constructor. A {@code null} tier defaults to
 * {@link Rarity#COMMON}, keeping legacy item data (which has no {@code rarity} field) backward
 * compatible.
 *
 * @param rarity  the rarity tier; never {@code null} after construction
 * @param affixes the ids of stat affixes attached to the item; never {@code null} after construction
 */
public record RarityProfile(Rarity rarity, List<AffixId> affixes) {

    private static final RarityProfile COMMON = new RarityProfile(Rarity.COMMON, List.of());

    public RarityProfile {
        rarity = Objects.requireNonNullElse(rarity, Rarity.COMMON);
        affixes = List.copyOf(Objects.requireNonNullElse(affixes, List.of()));
    }

    /**
     * Returns the shared common, affix-free profile.
     */
    public static RarityProfile common() {
        return COMMON;
    }

    /**
     * Returns a profile with the given tier and affixes.
     *
     * @param rarity  the rarity tier, or {@code null} to default to {@link Rarity#COMMON}
     * @param affixes the ids of stat affixes, or {@code null} for none
     */
    public static RarityProfile of(@Nullable Rarity rarity, @Nullable List<AffixId> affixes) {
        return new RarityProfile(rarity, affixes);
    }
}
