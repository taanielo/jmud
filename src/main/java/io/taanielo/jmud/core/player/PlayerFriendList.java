package io.taanielo.jmud.core.player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Holds the set of players a given player has added to their friends list via the {@code FRIEND}
 * command.
 *
 * <p>Friend names are case-insensitive and stored normalised to lower case. The relationship is
 * one-directional (A befriending B does not affect B's view of A) and requires no consent from the
 * befriended player, mirroring the precedent set by {@link PlayerIgnoreList}. The list is used to
 * highlight friends in {@code WHO} output.
 *
 * <p>Instances are immutable; the mutating operations {@link #with(String)} and
 * {@link #without(String)} return fresh copies.
 */
public class PlayerFriendList {

    private final Set<String> friendNames;

    /**
     * Creates a friend list from the given collection of usernames, normalising each to
     * lower case and discarding blanks and duplicates. Insertion order is preserved.
     *
     * @param friendNames the friend usernames; may be {@code null} for an empty list
     */
    public PlayerFriendList(Collection<String> friendNames) {
        Set<String> normalised = new LinkedHashSet<>();
        if (friendNames != null) {
            for (String name : friendNames) {
                if (name != null && !name.isBlank()) {
                    normalised.add(normalize(name));
                }
            }
        }
        this.friendNames = Set.copyOf(normalised);
    }

    /**
     * Returns an empty {@link PlayerFriendList} instance.
     */
    public static PlayerFriendList empty() {
        return new PlayerFriendList(List.of());
    }

    /**
     * Returns the normalised (lower-case) friend usernames, for inspection and persistence.
     */
    public Collection<String> friendNames() {
        return friendNames;
    }

    /**
     * Returns {@code true} when the given player name is on this friends list.
     *
     * @param playerName the username to check; case-insensitive, may be {@code null}
     */
    public boolean has(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        return friendNames.contains(normalize(playerName));
    }

    /**
     * Returns a copy of this list with the given player added, or this instance unchanged
     * when the player is already a friend.
     *
     * @param playerName the username to befriend; case-insensitive, must not be blank
     */
    public PlayerFriendList with(String playerName) {
        Objects.requireNonNull(playerName, "playerName is required");
        String key = normalize(playerName);
        if (friendNames.contains(key)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(friendNames);
        next.add(key);
        return new PlayerFriendList(next);
    }

    /**
     * Returns a copy of this list with the given player removed, or this instance unchanged
     * when the player is not a friend.
     *
     * @param playerName the username to unfriend; case-insensitive
     */
    public PlayerFriendList without(String playerName) {
        Objects.requireNonNull(playerName, "playerName is required");
        String key = normalize(playerName);
        if (!friendNames.contains(key)) {
            return this;
        }
        Set<String> next = new LinkedHashSet<>(friendNames);
        next.remove(key);
        return new PlayerFriendList(next);
    }

    /**
     * Returns {@code true} when no players are friends.
     */
    public boolean isEmpty() {
        return friendNames.isEmpty();
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
