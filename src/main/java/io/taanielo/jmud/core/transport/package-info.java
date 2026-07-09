/**
 * Tick-driven scheduled transport (ferries/boats) between dock rooms.
 *
 * <p>{@link io.taanielo.jmud.core.transport.BoatEngine} advances every configured
 * {@link io.taanielo.jmud.core.transport.Ferry} along its route on the tick thread (AGENTS.md §5).
 * When a ferry departs, the players standing in its deck room are carried to the next dock room and
 * notified through the {@link io.taanielo.jmud.core.messaging.MessageBroadcaster}. All schedule
 * state is confined to the tick thread and driven by tick counts (never wall-clock time) so the
 * arrival sequence is deterministic and replayable; any randomness is drawn from the
 * {@link io.taanielo.jmud.core.combat.CombatRandom} port.
 */
@NullMarked
package io.taanielo.jmud.core.transport;

import org.jspecify.annotations.NullMarked;
