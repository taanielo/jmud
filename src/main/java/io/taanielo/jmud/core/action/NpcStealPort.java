package io.taanielo.jmud.core.action;

import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Port that lets {@link GameActionService} interact with a stealable NPC in a room without
 * depending on the concrete mob layer.
 *
 * <p>The mob layer ({@code core.mob.MobRegistry}) already depends on {@code core.action}, so
 * defining this port here and having the mob layer implement it keeps the dependency direction
 * one-way (mob → action) and lets the rogue STEAL skill live in {@link GameActionService} while
 * mob state (gold, aggro) stays owned by the mob layer. All mutations happen on the tick thread
 * via the player command queue (AGENTS.md §5).
 */
public interface NpcStealPort {

    /**
     * A no-op port that never finds a target, used as the default when no mob layer is wired
     * (e.g. in unit tests of unrelated actions).
     */
    NpcStealPort NONE = (roomId, nameInput) -> Optional.empty();

    /**
     * Finds a live mob in the given room whose name matches (or is prefixed by) {@code nameInput}.
     *
     * @param roomId    the room the thief is currently in
     * @param nameInput the raw NPC-name input from the thief (case-insensitive, prefix match)
     * @return the matched steal victim, or empty when no mob in the room matches
     */
    Optional<StealVictim> findStealTarget(RoomId roomId, String nameInput);

    /**
     * A mob targeted by a pickpocket attempt. The {@link GameActionService} rolls the success
     * chance; this abstraction exposes only the mob-owned operations the skill needs: reading the
     * victim's stealable gold and turning it hostile toward the thief on a failed attempt.
     */
    interface StealVictim {

        /** Returns the victim's display name (e.g. {@code "bandit"}). */
        String name();

        /** Returns whether the victim carries any gold worth pilfering. */
        boolean hasStealableGold();

        /**
         * Rolls and returns the amount of gold lifted from the victim, through the mob layer's
         * seeded RNG port so the result is deterministic under a world seed (AGENTS.md §5).
         *
         * @return the pilfered gold amount (never negative)
         */
        int stealGold();

        /**
         * Marks the victim hostile toward the thief so it aggresses on subsequent ticks. Mobs
         * already engaged with the thief stay engaged; non-aggressive mobs become hostile.
         *
         * @param thief the player who was caught stealing
         */
        void turnHostile(Username thief);
    }
}
