package io.taanielo.jmud.core.ability;

import io.taanielo.jmud.core.player.PlayerVitals;

public record AbilityCost(int mana, int move) {
    public AbilityCost {
        if (mana < 0) {
            throw new IllegalArgumentException("Mana cost must be non-negative");
        }
        if (move < 0) {
            throw new IllegalArgumentException("Move cost must be non-negative");
        }
    }

    public boolean canAfford(PlayerVitals vitals) {
        if (vitals == null) {
            return false;
        }
        return vitals.mana() >= mana && vitals.move() >= move;
    }

    public PlayerVitals apply(PlayerVitals vitals) {
        PlayerVitals afterMana = vitals.consumeMana(mana);
        return afterMana.consumeMove(move);
    }
}
