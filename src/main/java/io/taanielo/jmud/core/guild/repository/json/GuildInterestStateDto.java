package io.taanielo.jmud.core.guild.repository.json;

/**
 * JSON transfer object for the world-scoped guild-interest accrual store
 * ({@code data/world-state/guild-interest-state.json}, issue #800).
 *
 * @param schemaVersion   the file schema version
 * @param gameDaysElapsed the running count of elapsed in-game days since the last interest payout boundary
 */
record GuildInterestStateDto(
    int schemaVersion,
    long gameDaysElapsed
) {
}
