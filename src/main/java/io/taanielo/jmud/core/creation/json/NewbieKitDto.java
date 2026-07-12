package io.taanielo.jmud.core.creation.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for the newbie starting-kit file ({@code data/newbie-kit.json}).
 *
 * @param schemaVersion the newbie-kit schema version
 * @param startingGold  the gold granted to a new character at creation
 * @param startingItems the items granted to a new character at creation
 */
public record NewbieKitDto(
    int schemaVersion,
    int startingGold,
    @Nullable List<NewbieKitItemDto> startingItems) {

    /**
     * JSON transfer object for a single starting-item entry.
     *
     * @param item     the granted item's id (matches {@code data/items/<id>.json})
     * @param quantity how many copies to grant; {@code null} defaults to one
     */
    public record NewbieKitItemDto(@Nullable String item, @Nullable Integer quantity) {
    }
}
