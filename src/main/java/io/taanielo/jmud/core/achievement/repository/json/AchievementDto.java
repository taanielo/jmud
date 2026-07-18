package io.taanielo.jmud.core.achievement.repository.json;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for an achievement definition file ({@code achievements/*.json}).
 */
record AchievementDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String name,
    @Nullable String description,
    @Nullable String condition,
    @Nullable Integer threshold,
    @Nullable String titleReward
) {
}
