package io.taanielo.jmud.core.combat;

import io.taanielo.jmud.core.player.Player;

public record CombatResult(
    Player attacker,
    Player target,
    boolean hit,
    boolean crit,
    int damage,
    String sourceMessage,
    String targetMessage,
    String roomMessage
) {
}
