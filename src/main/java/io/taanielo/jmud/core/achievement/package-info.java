/**
 * Achievement domain: immutable {@link io.taanielo.jmud.core.achievement.Achievement} milestone
 * definitions loaded from JSON, a per-player {@link io.taanielo.jmud.core.achievement.PlayerAchievements}
 * component tracking unlocked ids and unlock timestamps, and the stateless
 * {@link io.taanielo.jmud.core.achievement.AchievementService} that evaluates milestone conditions
 * (total kills, level) and unlocks any newly satisfied achievements.
 *
 * <p>Condition evaluation is deterministic and depends only on the player's persisted stats, not on
 * wall-clock time, so it is reproducible tick-to-tick (AGENTS.md §5). The only wall-clock value is
 * the unlock timestamp recorded as descriptive metadata, sourced from an injected
 * {@link java.time.Clock} so it stays testable. NullAway-checked ({@code @NullMarked}) since this is
 * a new package.
 */
@NullMarked
package io.taanielo.jmud.core.achievement;

import org.jspecify.annotations.NullMarked;
