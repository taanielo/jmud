/**
 * Infrastructure adapters that load {@link io.taanielo.jmud.core.achievement.Achievement} definitions
 * from {@code data/achievements/*.json} files into the domain
 * {@link io.taanielo.jmud.core.achievement.AchievementRepository} port. Constructed only by the
 * composition root (AGENTS.md §3.3). NullAway-checked ({@code @NullMarked}) since this is a new
 * package.
 */
@NullMarked
package io.taanielo.jmud.core.achievement.repository.json;

import org.jspecify.annotations.NullMarked;
