/**
 * JSON-backed infrastructure for the faction domain: loads {@code data/factions/*.json} definition
 * files into {@link io.taanielo.jmud.core.faction.Faction} value objects. Constructed only by the
 * composition root (AGENTS.md §3.3). NullAway-checked ({@code @NullMarked}).
 */
@NullMarked
package io.taanielo.jmud.core.faction.repository.json;

import org.jspecify.annotations.NullMarked;
