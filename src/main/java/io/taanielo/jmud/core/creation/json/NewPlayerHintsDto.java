package io.taanielo.jmud.core.creation.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for the new-player hints file ({@code data/new-player-hints.json}).
 *
 * @param schemaVersion the hints schema version
 * @param title         the heading rendered above the hint lines
 * @param lines         the hint lines rendered in order
 */
public record NewPlayerHintsDto(
    int schemaVersion,
    @Nullable String title,
    @Nullable List<String> lines) {
}
