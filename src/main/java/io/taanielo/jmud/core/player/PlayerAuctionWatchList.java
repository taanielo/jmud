package io.taanielo.jmud.core.player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Holds the keywords a player is watching on the Auction House via {@code AUCTION WATCH}.
 *
 * <p>When a new listing is created, the interface layer scans every online player's watch list and
 * sends a real-time notification for each keyword that matches the listed item's name (using the same
 * case-insensitive substring semantics as {@code AUCTION LIST <keyword>}). Watch keywords are stored
 * normalised to lower case with insertion order preserved, and are capped at {@value #MAX_WATCHES}
 * entries per player.
 *
 * <p>Instances are immutable; the mutating operations {@link #with(String)} and
 * {@link #without(String)} return fresh copies.
 */
public class PlayerAuctionWatchList {

    /** Maximum number of active watch keywords a single player may hold at once. */
    public static final int MAX_WATCHES = 10;

    private final Set<String> keywords;

    /**
     * Creates a watch list from the given collection of keywords, normalising each to lower case and
     * discarding blanks and duplicates. Insertion order is preserved. Should a persisted save somehow
     * carry more than {@link #MAX_WATCHES} keywords, the surplus is dropped defensively so the cap is
     * always upheld.
     *
     * @param keywords the watch keywords; may be {@code null} for an empty list
     */
    public PlayerAuctionWatchList(Collection<String> keywords) {
        Set<String> normalised = new LinkedHashSet<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && normalised.size() < MAX_WATCHES) {
                    normalised.add(normalize(keyword));
                }
            }
        }
        this.keywords = Set.copyOf(normalised);
    }

    /**
     * Returns an empty {@link PlayerAuctionWatchList} instance.
     */
    public static PlayerAuctionWatchList empty() {
        return new PlayerAuctionWatchList(List.of());
    }

    /**
     * Returns the normalised (lower-case) watch keywords in insertion order, for inspection and
     * persistence.
     */
    public Collection<String> keywords() {
        return keywords;
    }

    /**
     * Returns {@code true} when the given keyword is already being watched.
     *
     * @param keyword the keyword to check; case-insensitive, may be {@code null}
     */
    public boolean has(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        return keywords.contains(normalize(keyword));
    }

    /**
     * Returns the number of active watches.
     */
    public int size() {
        return keywords.size();
    }

    /**
     * Returns {@code true} when no keywords are being watched.
     */
    public boolean isEmpty() {
        return keywords.isEmpty();
    }

    /**
     * Returns {@code true} when the watch list has reached its {@link #MAX_WATCHES} cap and cannot
     * accept another distinct keyword.
     */
    public boolean isFull() {
        return keywords.size() >= MAX_WATCHES;
    }

    /**
     * Returns a copy of this list with the given keyword added, or this instance unchanged when the
     * keyword is already watched.
     *
     * @param keyword the keyword to watch; case-insensitive, must not be blank
     * @throws IllegalStateException when the list is already at its {@link #MAX_WATCHES} cap and the
     *                               keyword is not already present
     */
    public PlayerAuctionWatchList with(String keyword) {
        Objects.requireNonNull(keyword, "keyword is required");
        String key = normalize(keyword);
        if (keywords.contains(key)) {
            return this;
        }
        if (keywords.size() >= MAX_WATCHES) {
            throw new IllegalStateException("Auction watch list is full (max " + MAX_WATCHES + ").");
        }
        Set<String> next = new LinkedHashSet<>(keywords);
        next.add(key);
        return new PlayerAuctionWatchList(next);
    }

    /**
     * Returns a copy of this list with the given keyword removed, or this instance unchanged when the
     * keyword is not being watched.
     *
     * @param keyword the keyword to stop watching; case-insensitive
     */
    public PlayerAuctionWatchList without(String keyword) {
        Objects.requireNonNull(keyword, "keyword is required");
        String key = normalize(keyword);
        if (!keywords.contains(key)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(keywords);
        next.remove(key);
        return new PlayerAuctionWatchList(next);
    }

    private static String normalize(String keyword) {
        return keyword.trim().toLowerCase(Locale.ROOT);
    }
}
