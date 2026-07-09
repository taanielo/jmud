/**
 * Dynamic, tick-driven weather system.
 *
 * <p>{@link io.taanielo.jmud.core.weather.WeatherEngine} evolves a single global
 * {@link io.taanielo.jmud.core.weather.Weather} snapshot on the tick thread (AGENTS.md §5), which
 * outdoor rooms observe for their look descriptions and small combat/visibility modifiers. Weather
 * transitions are driven through the {@link io.taanielo.jmud.core.combat.CombatRandom} port so the
 * world stays deterministic and replayable.
 */
@NullMarked
package io.taanielo.jmud.core.weather;

import org.jspecify.annotations.NullMarked;
