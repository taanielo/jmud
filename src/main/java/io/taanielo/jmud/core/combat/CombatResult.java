package io.taanielo.jmud.core.combat;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

/**
 * The outcome of a single combat resolution, including updated target state,
 * messages to deliver, and the RNG seed used so any encounter is fully replayable.
 *
 * @param blocked              whether the target blocked the attack with an off-hand shield; a
 *                             blocked attack is still a {@link #hit()} but its damage was reduced
 *                             (not zeroed). Mutually exclusive with {@link #crit()}
 * @param effectTargetMessages messages delivered to the target when an on-hit status
 *                             effect was applied (e.g. "You are poisoned."); empty when
 *                             no effect was applied
 * @param effectRoomMessages   messages delivered to room occupants when an on-hit status
 *                             effect was applied; empty when no effect was applied
 * @param offhand              the resolved dual-wield off-hand attack for this round, or
 *                             {@code null} when the attacker was not dual-wielding a weapon in
 *                             their off-hand slot
 */
public record CombatResult(
    Player attacker,
    Player target,
    boolean hit,
    boolean crit,
    boolean blocked,
    int damage,
    String sourceMessage,
    String targetMessage,
    String roomMessage,
    // The per-encounter seed used to produce this result; 0 when unseeded.
    long rngSeed,
    List<String> effectTargetMessages,
    List<String> effectRoomMessages,
    OffhandResult offhand
) {
    public CombatResult {
        effectTargetMessages = List.copyOf(Objects.requireNonNullElse(effectTargetMessages, List.of()));
        effectRoomMessages = List.copyOf(Objects.requireNonNullElse(effectRoomMessages, List.of()));
    }

    /**
     * Convenience constructor for a single-attack round (no dual-wield off-hand attack).
     */
    public CombatResult(
        Player attacker,
        Player target,
        boolean hit,
        boolean crit,
        boolean blocked,
        int damage,
        String sourceMessage,
        String targetMessage,
        String roomMessage,
        long rngSeed,
        List<String> effectTargetMessages,
        List<String> effectRoomMessages
    ) {
        this(attacker, target, hit, crit, blocked, damage, sourceMessage, targetMessage, roomMessage, rngSeed,
            effectTargetMessages, effectRoomMessages, null);
    }

    /**
     * Convenience constructor for results that apply no on-hit status effect.
     */
    public CombatResult(
        Player attacker,
        Player target,
        boolean hit,
        boolean crit,
        boolean blocked,
        int damage,
        String sourceMessage,
        String targetMessage,
        String roomMessage,
        long rngSeed
    ) {
        this(attacker, target, hit, crit, blocked, damage, sourceMessage, targetMessage, roomMessage, rngSeed,
            List.of(), List.of(), null);
    }

    /**
     * The outcome of a dual-wield off-hand attack resolved after the main-hand attack in the same
     * combat round. The off-hand attack is an independent roll against the same target, using the
     * off-hand weapon's own attack definition at a reduced hit chance and damage.
     *
     * @param hit                  whether the off-hand attack landed
     * @param crit                 whether the off-hand attack was a critical hit
     * @param blocked              whether the target blocked the off-hand attack with a shield
     * @param damage               damage dealt by the off-hand attack (already reduced)
     * @param sourceMessage        message shown to the attacker for the off-hand swing
     * @param targetMessage        message shown to the target for the off-hand swing
     * @param roomMessage          message shown to room occupants for the off-hand swing
     * @param effectTargetMessages on-hit status effect messages for the target; empty when none
     * @param effectRoomMessages   on-hit status effect messages for the room; empty when none
     */
    public record OffhandResult(
        boolean hit,
        boolean crit,
        boolean blocked,
        int damage,
        String sourceMessage,
        String targetMessage,
        String roomMessage,
        List<String> effectTargetMessages,
        List<String> effectRoomMessages
    ) {
        public OffhandResult {
            effectTargetMessages = List.copyOf(Objects.requireNonNullElse(effectTargetMessages, List.of()));
            effectRoomMessages = List.copyOf(Objects.requireNonNullElse(effectRoomMessages, List.of()));
        }
    }
}
