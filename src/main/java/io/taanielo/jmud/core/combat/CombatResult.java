package io.taanielo.jmud.core.combat;

import io.taanielo.jmud.core.player.Player;

/**
 * The outcome of a single combat resolution, including updated target state,
 * messages to deliver, and the RNG seed used so any encounter is fully replayable.
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
    long rngSeed
) {
}
