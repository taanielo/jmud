/**
 * Faction and reputation domain: immutable {@link io.taanielo.jmud.core.faction.Faction} definitions
 * loaded from JSON, a per-player {@link io.taanielo.jmud.core.faction.PlayerReputation} standing map,
 * and the stateless {@link io.taanielo.jmud.core.faction.ReputationService} that adjusts standing when
 * a faction's mob is slain and resolves reputation-driven shop prices and mob aggression.
 *
 * <p>Standing is a signed integer (positive = friendly, negative = hostile, zero = neutral) and all
 * calculations are deterministic (no wall-clock time), so behaviour is reproducible tick-to-tick
 * (AGENTS.md §5). NullAway-checked ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.faction;

import org.jspecify.annotations.NullMarked;
