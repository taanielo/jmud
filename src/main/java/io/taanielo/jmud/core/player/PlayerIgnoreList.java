package io.taanielo.jmud.core.player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Holds the set of players a given player has chosen to ignore via the {@code IGNORE} command.
 *
 * <p>Ignored names are case-insensitive and stored normalised to lower case. TELL and SAY
 * messages originating from an ignored player are silently dropped for the ignoring player;
 * the ignore relationship is one-directional (A ignoring B does not affect B's view of A).
 *
 * <p>Instances are immutable; the mutating operations {@link #with(String)} and
 * {@link #without(String)} return fresh copies.
 */
public class PlayerIgnoreList {

    private final Set<String> ignoredNames;

    /**
     * Creates an ignore list from the given collection of usernames, normalising each to
     * lower case and discarding blanks and duplicates. Insertion order is preserved.
     *
     * @param ignoredNames the ignored usernames; may be {@code null} for an empty list
     */
    public PlayerIgnoreList(Collection<String> ignoredNames) {
        Set<String> normalised = new LinkedHashSet<>();
        if (ignoredNames != null) {
            for (String name : ignoredNames) {
                if (name != null && !name.isBlank()) {
                    normalised.add(normalize(name));
                }
            }
        }
        this.ignoredNames = Set.copyOf(normalised);
    }

    /**
     * Returns an empty {@link PlayerIgnoreList} instance.
     */
    public static PlayerIgnoreList empty() {
        return new PlayerIgnoreList(List.of());
    }

    /**
     * Returns the normalised (lower-case) ignored usernames, for inspection and persistence.
     */
    public Collection<String> ignoredNames() {
        return ignoredNames;
    }

    /**
     * Returns {@code true} when the given player name is on this ignore list.
     *
     * @param playerName the username to check; case-insensitive, may be {@code null}
     */
    public boolean has(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        return ignoredNames.contains(normalize(playerName));
    }

    /**
     * Returns a copy of this list with the given player added, or this instance unchanged
     * when the player is already ignored.
     *
     * @param playerName the username to ignore; case-insensitive, must not be blank
     */
    public PlayerIgnoreList with(String playerName) {
        Objects.requireNonNull(playerName, "playerName is required");
        String key = normalize(playerName);
        if (ignoredNames.contains(key)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(ignoredNames);
        next.add(key);
        return new PlayerIgnoreList(next);
    }

    /**
     * Returns a copy of this list with the given player removed, or this instance unchanged
     * when the player is not ignored.
     *
     * @param playerName the username to stop ignoring; case-insensitive
     */
    public PlayerIgnoreList without(String playerName) {
        Objects.requireNonNull(playerName, "playerName is required");
        String key = normalize(playerName);
        if (!ignoredNames.contains(key)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(ignoredNames);
        next.remove(key);
        return new PlayerIgnoreList(next);
    }

    /**
     * Returns {@code true} when no players are ignored.
     */
    public boolean isEmpty() {
        return ignoredNames.isEmpty();
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
