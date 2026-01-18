package io.taanielo.jmud.core.ability;

public record AbilityCost(int mana, int move) {
    public AbilityCost {
        if (mana < 0) {
            throw new IllegalArgumentException("Mana cost must be non-negative");
        }
        if (move < 0) {
            throw new IllegalArgumentException("Move cost must be non-negative");
        }
    }
}
