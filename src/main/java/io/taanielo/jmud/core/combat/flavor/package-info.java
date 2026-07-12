/**
 * Combat flavor tables: data-driven, deterministic mappings from raw numbers to classic-MUD words.
 *
 * <p>{@link io.taanielo.jmud.core.combat.flavor.DamageVerbTable} turns a hit's damage (expressed as a
 * percentage of the target's maximum HP) into a tiered verb ("scratches" … "MASSACRES"), and
 * {@link io.taanielo.jmud.core.combat.flavor.TargetConditionTable} turns a combatant's current HP
 * (as a percentage of its maximum) into a condition phrase ("is in perfect condition" … "is in awful
 * condition"). Both are pure integer math over their inputs — no RNG, no wall-clock, no I/O — so the
 * same inputs always yield the same word (AGENTS.md §5).
 *
 * <p>The word tables live in versioned JSON under {@code data/combat/} and are loaded through the
 * {@link io.taanielo.jmud.core.combat.flavor.repository.CombatFlavorRepository} port; only the
 * composition root constructs the JSON implementation.
 */
@NullMarked
package io.taanielo.jmud.core.combat.flavor;

import org.jspecify.annotations.NullMarked;
