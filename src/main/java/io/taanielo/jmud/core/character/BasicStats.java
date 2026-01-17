package io.taanielo.jmud.core.character;

public class BasicStats implements Stats {

    private final int hp;
    private final int maxHp;
    private final int mana;
    private final int maxMana;
    private final int strength;
    private final int agility;

    private BasicStats(int hp, int maxHp, int mana, int maxMana, int strength, int agility) {
        if (maxHp < 0) {
            throw new IllegalArgumentException("Max HP must be non-negative");
        }
        if (hp < 0) {
            throw new IllegalArgumentException("HP must be non-negative");
        }
        if (hp > maxHp) {
            throw new IllegalArgumentException("HP cannot exceed max HP");
        }
        if (maxMana < 0) {
            throw new IllegalArgumentException("Max mana must be non-negative");
        }
        if (mana < 0) {
            throw new IllegalArgumentException("Mana must be non-negative");
        }
        if (mana > maxMana) {
            throw new IllegalArgumentException("Mana cannot exceed max mana");
        }
        if (strength < 0) {
            throw new IllegalArgumentException("Strength must be non-negative");
        }
        if (agility < 0) {
            throw new IllegalArgumentException("Agility must be non-negative");
        }
        this.hp = hp;
        this.maxHp = maxHp;
        this.mana = mana;
        this.maxMana = maxMana;
        this.strength = strength;
        this.agility = agility;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int hp;
        private int maxHp;
        private int mana;
        private int maxMana;
        private int strength;
        private int agility;

        public Builder hp(int hp) {
            this.hp = hp;
            return this;
        }

        public Builder maxHp(int maxHp) {
            this.maxHp = maxHp;
            return this;
        }

        public Builder mana(int mana) {
            this.mana = mana;
            return this;
        }

        public Builder maxMana(int maxMana) {
            this.maxMana = maxMana;
            return this;
        }

        public Builder strength(int strength) {
            this.strength = strength;
            return this;
        }

        public Builder agility(int agility) {
            this.agility = agility;
            return this;
        }

        public BasicStats build() {
            return new BasicStats(hp, maxHp, mana, maxMana, strength, agility);
        }
    }

    @Override
    public int hp() {
        return hp;
    }

    @Override
    public int maxHp() {
        return maxHp;
    }

    @Override
    public int mana() {
        return mana;
    }

    @Override
    public int maxMana() {
        return maxMana;
    }

    @Override
    public int strength() {
        return strength;
    }

    @Override
    public int agility() {
        return agility;
    }

    @Override
    public Stats damage(int amount) {
        validateAmount(amount, "Damage");
        int nextHp = Math.max(0, hp - amount);
        return new BasicStats(nextHp, maxHp, mana, maxMana, strength, agility);
    }

    @Override
    public Stats heal(int amount) {
        validateAmount(amount, "Heal");
        int nextHp = Math.min(maxHp, hp + amount);
        return new BasicStats(nextHp, maxHp, mana, maxMana, strength, agility);
    }

    @Override
    public Stats consumeMana(int amount) {
        validateAmount(amount, "Mana consumption");
        int nextMana = Math.max(0, mana - amount);
        return new BasicStats(hp, maxHp, nextMana, maxMana, strength, agility);
    }

    @Override
    public Stats restoreMana(int amount) {
        validateAmount(amount, "Mana restoration");
        int nextMana = Math.min(maxMana, mana + amount);
        return new BasicStats(hp, maxHp, nextMana, maxMana, strength, agility);
    }

    private void validateAmount(int amount, String label) {
        if (amount < 0) {
            throw new IllegalArgumentException(label + " amount must be non-negative");
        }
    }
}
