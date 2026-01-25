package io.taanielo.jmud.core.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PlayerVitals {
    public static final int DEFAULT_MAX = 20;

    private final int hp;
    private final int maxHp;
    private final int baseMaxHp;
    private final int mana;
    private final int maxMana;
    private final int move;
    private final int maxMove;

    public static PlayerVitals defaults() {
        return new PlayerVitals(DEFAULT_MAX, DEFAULT_MAX, DEFAULT_MAX, DEFAULT_MAX, DEFAULT_MAX, DEFAULT_MAX);
    }

    public PlayerVitals(
        int hp,
        int maxHp,
        int mana,
        int maxMana,
        int move,
        int maxMove
    ) {
        this(hp, maxHp, maxHp, mana, maxMana, move, maxMove);
    }

    @JsonCreator
    public PlayerVitals(
        @JsonProperty("hp") int hp,
        @JsonProperty("maxHp") int maxHp,
        @JsonProperty("baseMaxHp") Integer baseMaxHp,
        @JsonProperty("mana") int mana,
        @JsonProperty("maxMana") int maxMana,
        @JsonProperty("move") int move,
        @JsonProperty("maxMove") int maxMove
    ) {
        int resolvedBaseMaxHp = baseMaxHp == null || baseMaxHp <= 0 ? maxHp : baseMaxHp;
        validate(hp, maxHp, "hp");
        validateMax(resolvedBaseMaxHp, "base max hp");
        validate(mana, maxMana, "mana");
        validate(move, maxMove, "move");
        this.hp = hp;
        this.maxHp = maxHp;
        this.baseMaxHp = resolvedBaseMaxHp;
        this.mana = mana;
        this.maxMana = maxMana;
        this.move = move;
        this.maxMove = maxMove;
    }

    private static void validate(int value, int max, String label) {
        validateMax(max, "max " + label);
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
        if (value > max) {
            throw new IllegalArgumentException(label + " cannot exceed max " + label);
        }
    }

    private static void validateMax(int max, String label) {
        if (max <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
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

    public int baseMaxHp() {
        return baseMaxHp;
    }

    public int getBaseMaxHp() {
        return baseMaxHp;
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
        return new PlayerVitals(nextHp, maxHp, baseMaxHp, mana, maxMana, move, maxMove);
    }

    public PlayerVitals heal(int amount) {
        validateAmount(amount, "Heal");
        int nextHp = Math.min(maxHp, hp + amount);
        return new PlayerVitals(nextHp, maxHp, baseMaxHp, mana, maxMana, move, maxMove);
    }

    public PlayerVitals withMaxHp(int maxHp) {
        validateMax(maxHp, "max hp");
        int nextHp = Math.min(hp, maxHp);
        return new PlayerVitals(nextHp, maxHp, baseMaxHp, mana, maxMana, move, maxMove);
    }

    public PlayerVitals consumeMana(int amount) {
        validateAmount(amount, "Mana consumption");
        int nextMana = Math.max(0, mana - amount);
        return new PlayerVitals(hp, maxHp, baseMaxHp, nextMana, maxMana, move, maxMove);
    }

    public PlayerVitals restoreMana(int amount) {
        validateAmount(amount, "Mana restoration");
        int nextMana = Math.min(maxMana, mana + amount);
        return new PlayerVitals(hp, maxHp, baseMaxHp, nextMana, maxMana, move, maxMove);
    }

    public PlayerVitals consumeMove(int amount) {
        validateAmount(amount, "Move consumption");
        int nextMove = Math.max(0, move - amount);
        return new PlayerVitals(hp, maxHp, baseMaxHp, mana, maxMana, nextMove, maxMove);
    }

    public PlayerVitals restoreMove(int amount) {
        validateAmount(amount, "Move restoration");
        int nextMove = Math.min(maxMove, move + amount);
        return new PlayerVitals(hp, maxHp, baseMaxHp, mana, maxMana, nextMove, maxMove);
    }

    public PlayerVitals respawnHalf() {
        return new PlayerVitals(
            halfOf(maxHp),
            maxHp,
            baseMaxHp,
            halfOf(maxMana),
            maxMana,
            halfOf(maxMove),
            maxMove
        );
    }

    private void validateAmount(int amount, String label) {
        if (amount < 0) {
            throw new IllegalArgumentException(label + " amount must be non-negative");
        }
    }

    private int halfOf(int max) {
        return Math.max(1, max / 2);
    }
}
