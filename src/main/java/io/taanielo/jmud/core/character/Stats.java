package io.taanielo.jmud.core.character;

public interface Stats {
    int hp();
    int maxHp();
    int mana();
    int maxMana();
    int strength();
    int agility();

    Stats damage(int amount);
    Stats heal(int amount);
    Stats consumeMana(int amount);
    Stats restoreMana(int amount);
}
