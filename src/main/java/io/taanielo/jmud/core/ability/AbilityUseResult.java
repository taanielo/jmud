package io.taanielo.jmud.core.ability;

import java.util.List;

import io.taanielo.jmud.core.player.Player;

/**
 * The result of using an ability.
 *
 * <p>For single-target abilities, {@code source} and {@code target} are the updated
 * players and {@code groupTargets} is empty. For group abilities
 * ({@link AbilityTargeting#BENEFICIAL_GROUP}), {@code groupTargets} contains the
 * updated state of every player that was affected (including the caster).
 */
public record AbilityUseResult(
    Player source,
    Player target,
    List<String> messages,
    List<Player> groupTargets
) {
    /**
     * Convenience constructor for single-target ability results.
     * {@code groupTargets} defaults to an empty list.
     */
    public AbilityUseResult(Player source, Player target, List<String> messages) {
        this(source, target, messages, List.of());
    }
}
