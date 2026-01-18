package io.taanielo.jmud.core.ability;

import io.taanielo.jmud.core.player.Player;

public interface AbilityCostResolver {
    boolean canAfford(Player player, AbilityCost cost);

    Player applyCost(Player player, AbilityCost cost);
}
