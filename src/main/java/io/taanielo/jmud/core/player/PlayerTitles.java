package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Holds the titles a player has earned, e.g. by completing quests.
 *
 * <p>Titles are unique; earning the same title more than once is a no-op.
 */
public class PlayerTitles {
    private final List<String> earned;

    public PlayerTitles(List<String> earned) {
        this.earned = List.copyOf(Objects.requireNonNullElse(earned, List.of()));
    }

    /**
     * Returns an empty {@link PlayerTitles} instance.
     */
    public static PlayerTitles empty() {
        return new PlayerTitles(List.of());
    }

    /**
     * Returns the titles earned so far, in the order they were granted.
     */
    public List<String> earned() {
        return earned;
    }

    /**
     * Returns {@code true} when the given title has already been earned.
     *
     * @param title the title to check; must not be null
     */
    public boolean has(String title) {
        Objects.requireNonNull(title, "title is required");
        return earned.contains(title);
    }

    /**
     * Returns a copy of this component with the given titles replacing the current set.
     *
     * @param nextEarned the new title list
     */
    public PlayerTitles withEarned(List<String> nextEarned) {
        return new PlayerTitles(nextEarned);
    }

    /**
     * Returns a copy of this component with the given title added, unless it was
     * already earned, in which case this instance is returned unchanged.
     *
     * @param title the title to grant; must not be null
     */
    public PlayerTitles grant(String title) {
        Objects.requireNonNull(title, "title is required");
        if (earned.contains(title)) {
            return this;
        }
        List<String> next = new ArrayList<>(earned);
        next.add(title);
        return new PlayerTitles(next);
    }
}
