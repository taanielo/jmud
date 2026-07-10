/**
 * JSON-backed persistence for player guilds: one {@code data/guilds/<guild-id>.json} file per guild,
 * written behind a single dedicated virtual thread so the tick thread never blocks on disk I/O
 * (AGENTS.md §5). NullAway-checked ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.guild.repository.json;

import org.jspecify.annotations.NullMarked;
