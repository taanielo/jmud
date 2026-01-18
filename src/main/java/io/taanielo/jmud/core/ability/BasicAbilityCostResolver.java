package io.taanielo.jmud.core.ability;

import java.util.Objects;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

public class BasicAbilityCostResolver implements AbilityCostResolver {
    @Override
    public boolean canAfford(Player player, AbilityCost cost) {
        Objects.requireNonNull(player, "Player is required");
        Objects.requireNonNull(cost, "Ability cost is required");
        PlayerVitals vitals = player.getVitals();
        return vitals.mana() >= cost.mana() && vitals.move() >= cost.move();
    }

    @Override
    public Player applyCost(Player player, AbilityCost cost) {
        Objects.requireNonNull(player, "Player is required");
        Objects.requireNonNull(cost, "Ability cost is required");
        PlayerVitals vitals = player.getVitals();
        PlayerVitals updated = vitals.consumeMana(cost.mana()).consumeMove(cost.move());
        return player.withVitals(updated);
    }
}
