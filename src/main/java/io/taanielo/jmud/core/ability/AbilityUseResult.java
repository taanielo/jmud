package io.taanielo.jmud.core.ability;

import java.util.List;

import io.taanielo.jmud.core.player.Player;

public record AbilityUseResult(Player source, Player target, List<String> messages) {
}
