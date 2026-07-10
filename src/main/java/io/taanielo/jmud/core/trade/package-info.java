/**
 * Secure two-way player-to-player trading.
 *
 * <p>Players standing in the same room can open a {@link io.taanielo.jmud.core.trade.TradeSession},
 * stage items and gold on each side of the offer, and swap ownership atomically once both parties
 * confirm matching offers. The {@link io.taanielo.jmud.core.trade.TradeService} owns all session
 * bookkeeping in memory (nothing is persisted); the {@link io.taanielo.jmud.core.trade.TradeExecutionService}
 * performs the pure, deterministic atomic swap.
 *
 * <p>All state mutation runs on the tick thread per AGENTS.md §5; service methods are additionally
 * {@code synchronized} so reader-thread cleanup never corrupts session state.
 */
@NullMarked
package io.taanielo.jmud.core.trade;

import org.jspecify.annotations.NullMarked;
