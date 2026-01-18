package io.taanielo.jmud.core.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PlayerVitals {
    public static final int DEFAULT_MAX = 20;

    private final int hp;
    private final int maxHp;
    private final int mana;
    private final int maxMana;
    private final int move;
    private final int maxMove;

    public static PlayerVitals defaults() {
        return new PlayerVitals(DEFAULT_MAX, DEFAULT_MAX, DEFAULT_MAX, DEFAULT_MAX, DEFAULT_MAX, DEFAULT_MAX);
    }

    @JsonCreator
    public PlayerVitals(
        @JsonProperty("hp") int hp,
        @JsonProperty("maxHp") int maxHp,
        @JsonProperty("mana") int mana,
        @JsonProperty("maxMana") int maxMana,
        @JsonProperty("move") int move,
        @JsonProperty("maxMove") int maxMove
    ) {
        validate(hp, maxHp, "hp");
        validate(mana, maxMana, "mana");
        validate(move, maxMove, "move");
        this.hp = hp;
        this.maxHp = maxHp;
        this.mana = mana;
        this.maxMana = maxMana;
        this.move = move;
        this.maxMove = maxMove;
    }

    private static void validate(int value, int max, String label) {
        if (max <= 0) {
            throw new IllegalArgumentException("Max " + label + " must be positive");
        }
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
        if (value > max) {
            throw new IllegalArgumentException(label + " cannot exceed max " + label);
        }
    }

    public int hp() {
        return hp;
    }

    public int getHp() {
        return hp;
    }

    public int maxHp() {
        return maxHp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int mana() {
        return mana;
    }

    public int getMana() {
        return mana;
    }

    public int maxMana() {
        return maxMana;
    }

    public int getMaxMana() {
        return maxMana;
    }

    public int move() {
        return move;
    }

    public int getMove() {
        return move;
    }

    public int maxMove() {
        return maxMove;
    }

    public int getMaxMove() {
        return maxMove;
    }

    public PlayerVitals damage(int amount) {
        validateAmount(amount, "Damage");
        int nextHp = Math.max(0, hp - amount);
        return new PlayerVitals(nextHp, maxHp, mana, maxMana, move, maxMove);
    }

    public PlayerVitals heal(int amount) {
        validateAmount(amount, "Heal");
        int nextHp = Math.min(maxHp, hp + amount);
        return new PlayerVitals(nextHp, maxHp, mana, maxMana, move, maxMove);
    }

    public PlayerVitals consumeMana(int amount) {
        validateAmount(amount, "Mana consumption");
        int nextMana = Math.max(0, mana - amount);
        return new PlayerVitals(hp, maxHp, nextMana, maxMana, move, maxMove);
    }

    public PlayerVitals restoreMana(int amount) {
        validateAmount(amount, "Mana restoration");
        int nextMana = Math.min(maxMana, mana + amount);
        return new PlayerVitals(hp, maxHp, nextMana, maxMana, move, maxMove);
    }

    public PlayerVitals consumeMove(int amount) {
        validateAmount(amount, "Move consumption");
        int nextMove = Math.max(0, move - amount);
        return new PlayerVitals(hp, maxHp, mana, maxMana, nextMove, maxMove);
    }

    private void validateAmount(int amount, String label) {
        if (amount < 0) {
            throw new IllegalArgumentException(label + " amount must be non-negative");
        }
    }
}
