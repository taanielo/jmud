package io.taanielo.jmud.core.ability;

import java.util.Optional;

import io.taanielo.jmud.core.player.Player;

public interface AbilityTargetResolver {
    Optional<Player> resolve(Player source, String targetInput);
}
