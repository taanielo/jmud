package io.taanielo.jmud.core.combat;

import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

/**
 * Represents a single combat action between two players.
 */
public record CombatAction(Player attacker, Player target, AttackId attackId) {
    public CombatAction {
        Objects.requireNonNull(attacker, "Attacker is required");
        Objects.requireNonNull(target, "Target is required");
        Objects.requireNonNull(attackId, "Attack id is required");
    }
}
