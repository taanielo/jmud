/**
 * The mentor-bond social system (see the MENTOR command).
 *
 * <p>A mentor bond is a one-to-one, opt-in link between a higher-level player (the mentor) and a
 * lower-level newcomer (the mentee). While the two are grouped and both share in the same mob kill,
 * the mentee earns a modest flat XP bonus on top of their normal party split. The mentor climbs the
 * data-driven Mentors' Guild rank ladder as they graduate mentees: each rung grants a unique
 * milestone title and unlocks a shared guild perk — a bonus to the mentor's own XP while actively
 * mentoring — that grows with their standing.
 *
 * <p>{@link io.taanielo.jmud.core.mentor.MentorService} owns the transient proposal registry
 * (tick-thread-owned, mirroring {@link io.taanielo.jmud.core.social.MarriageService}) and the pure
 * domain rules for eligibility, the XP bonuses, graduation, and the rank ladder. The persisted bond
 * itself lives on {@link io.taanielo.jmud.core.player.Player}, defaulting to "no bond" for existing
 * saves; the rank ladder is game content under {@code data/mentor/ranks.json}.
 */
@NullMarked
package io.taanielo.jmud.core.mentor;

import org.jspecify.annotations.NullMarked;
