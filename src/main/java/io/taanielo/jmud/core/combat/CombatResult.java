package io.taanielo.jmud.core.combat;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

/**
 * The outcome of a single combat resolution, including updated target state,
 * messages to deliver, and the RNG seed used so any encounter is fully replayable.
 *
 * @param effectTargetMessages messages delivered to the target when an on-hit status
 *                             effect was applied (e.g. "You are poisoned."); empty when
 *                             no effect was applied
 * @param effectRoomMessages   messages delivered to room occupants when an on-hit status
 *                             effect was applied; empty when no effect was applied
 */
public record CombatResult(
    Player attacker,
    Player target,
    boolean hit,
    boolean crit,
    int damage,
    String sourceMessage,
    String targetMessage,
    String roomMessage,
    // The per-encounter seed used to produce this result; 0 when unseeded.
    long rngSeed,
    List<String> effectTargetMessages,
    List<String> effectRoomMessages
) {
    public CombatResult {
        effectTargetMessages = List.copyOf(Objects.requireNonNullElse(effectTargetMessages, List.of()));
        effectRoomMessages = List.copyOf(Objects.requireNonNullElse(effectRoomMessages, List.of()));
    }

    /**
     * Convenience constructor for results that apply no on-hit status effect.
     */
    public CombatResult(
        Player attacker,
        Player target,
        boolean hit,
        boolean crit,
        int damage,
        String sourceMessage,
        String targetMessage,
        String roomMessage,
        long rngSeed
    ) {
        this(attacker, target, hit, crit, damage, sourceMessage, targetMessage, roomMessage, rngSeed, List.of(), List.of());
    }
}
