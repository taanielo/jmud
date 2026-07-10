/**
 * Player guild (clan) domain: persistent, player-founded social units that survive logout and server
 * restarts, unlike the session-scoped {@link io.taanielo.jmud.core.party party} system.
 *
 * <p>A {@link io.taanielo.jmud.core.guild.Guild} owns an immutable roster of
 * {@link io.taanielo.jmud.core.guild.GuildMember}s (each with a {@link io.taanielo.jmud.core.guild.GuildRank}
 * and join order) and a designated leader. The {@link io.taanielo.jmud.core.guild.GuildService}
 * application service is the authoritative owner of all guild state; it caches every guild in memory
 * (loaded once at startup) and hands mutations to the {@link io.taanielo.jmud.core.guild.GuildRepository}
 * for write-behind persistence, so no blocking disk I/O ever happens on the tick thread (AGENTS.md §5).
 *
 * <p>All mutation happens on the tick thread via the player command queue; the service is nonetheless
 * defensively synchronized so its in-memory indices stay consistent. NullAway-checked
 * ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.guild;

import org.jspecify.annotations.NullMarked;
