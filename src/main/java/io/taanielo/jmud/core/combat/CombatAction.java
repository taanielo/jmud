package io.taanielo.jmud.core.combat;

import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

/**
 * Represents a single combat action between two players.
 *
 * <p>{@code environmentHitModifier} and {@code environmentRangedHitModifier} carry ambient
 * hit-chance deltas contributed by the surrounding environment (currently weather; see
 * {@link io.taanielo.jmud.core.weather.Weather}). The first applies to every attack, the second is
 * added only when the chosen attack is ranged. Both default to {@code 0} for the plain three-arg
 * constructor so existing callers are unaffected.
 *
 * @param attacker                     the attacking player
 * @param target                       the defending player
 * @param attackId                     the attack being used
 * @param environmentHitModifier       hit-chance delta applied to all attacks (usually negative)
 * @param environmentRangedHitModifier extra hit-chance delta applied only to ranged attacks
 */
public record CombatAction(
    Player attacker,
    Player target,
    AttackId attackId,
    int environmentHitModifier,
    int environmentRangedHitModifier
) {
    public CombatAction {
        Objects.requireNonNull(attacker, "Attacker is required");
        Objects.requireNonNull(target, "Target is required");
        Objects.requireNonNull(attackId, "Attack id is required");
    }

    /**
     * Creates a combat action with no environmental modifiers.
     *
     * @param attacker the attacking player
     * @param target   the defending player
     * @param attackId the attack being used
     */
    public CombatAction(Player attacker, Player target, AttackId attackId) {
        this(attacker, target, attackId, 0, 0);
    }
}
