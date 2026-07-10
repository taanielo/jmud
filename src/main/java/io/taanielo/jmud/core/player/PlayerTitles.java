package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Holds the titles a player has earned, e.g. by completing quests, plus the single
 * title (if any) the player has selected to display next to their name.
 *
 * <p>Titles are unique; earning the same title more than once is a no-op. The active
 * title is always one of the earned titles; an active title that is not (or no longer)
 * earned is dropped, so stale save data can never surface an unearned active title.
 */
public class PlayerTitles {
    private final List<String> earned;
    private final @Nullable String active;

    public PlayerTitles(List<String> earned) {
        this(earned, null);
    }

    public PlayerTitles(List<String> earned, @Nullable String active) {
        this.earned = List.copyOf(Objects.requireNonNullElse(earned, List.of()));
        this.active = normalizeActive(active);
    }

    private @Nullable String normalizeActive(@Nullable String candidate) {
        if (candidate == null) {
            return null;
        }
        return earned.stream()
            .filter(title -> title.equalsIgnoreCase(candidate))
            .findFirst()
            .orElse(null);
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
     * Returns the currently active (displayed) title, or {@code null} when none is selected.
     */
    public @Nullable String active() {
        return active;
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
     * Returns the earned title matching the given name case-insensitively, or empty when the
     * player has not earned any title with that name.
     *
     * @param title the title name to match; must not be null
     */
    public Optional<String> matchEarned(String title) {
        Objects.requireNonNull(title, "title is required");
        return earned.stream()
            .filter(candidate -> candidate.equalsIgnoreCase(title))
            .findFirst();
    }

    /**
     * Returns a copy of this component with the given titles replacing the current set. The
     * active title is retained only when it is still present in the new set.
     *
     * @param nextEarned the new title list
     */
    public PlayerTitles withEarned(List<String> nextEarned) {
        return new PlayerTitles(nextEarned, active);
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
        return new PlayerTitles(next, active);
    }

    /**
     * Returns a copy of this component with the given title selected as active.
     *
     * @param title the title to activate; must be an earned title (matched case-insensitively)
     * @throws IllegalArgumentException when the title has not been earned
     */
    public PlayerTitles withActive(String title) {
        Objects.requireNonNull(title, "title is required");
        String canonical = matchEarned(title)
            .orElseThrow(() -> new IllegalArgumentException("Title not earned: " + title));
        return new PlayerTitles(earned, canonical);
    }

    /**
     * Returns a copy of this component with no active title, or this instance unchanged when
     * no title was active.
     */
    public PlayerTitles clearActive() {
        return active == null ? this : new PlayerTitles(earned, null);
    }
}
