package io.taanielo.jmud.core.creation;

import java.util.List;
import java.util.Objects;

/**
 * The one-time "getting started" hint block shown to a brand-new character immediately after
 * character creation completes.
 *
 * <p>A first-time player can be killed by the starter area before they discover the tools that would
 * have saved them — {@code CONSIDER} to gauge a mob, {@code EQUIP}/{@code WIELD} to arm themselves,
 * {@code FLEE} to escape a losing fight. Nothing else surfaces those commands at level 1, so this
 * block teaches them up front. The wording is game content stored as versioned JSON
 * ({@code data/new-player-hints.json}) so content authors can iterate without code changes
 * (AGENTS.md §11); this record is an immutable domain snapshot of that definition.
 *
 * @param title the heading rendered above the hint lines (e.g. {@code "Getting Started"})
 * @param lines the hint lines rendered in order; may be empty, in which case nothing is shown
 */
public record NewPlayerHints(String title, List<String> lines) {

    /** A hints block that shows nothing, used as a safe fallback when no hint data is configured. */
    public static final NewPlayerHints EMPTY = new NewPlayerHints("Getting Started", List.of());

    /**
     * Canonical constructor.
     *
     * @param title the heading rendered above the hint lines; must not be null
     * @param lines the hint lines rendered in order; must not be null
     */
    public NewPlayerHints {
        Objects.requireNonNull(title, "Title is required");
        lines = List.copyOf(Objects.requireNonNull(lines, "Lines are required"));
    }

    /**
     * Reports whether this block has any hint lines to render.
     *
     * @return {@code true} when there is at least one line to show
     */
    public boolean hasLines() {
        return !lines.isEmpty();
    }
}
