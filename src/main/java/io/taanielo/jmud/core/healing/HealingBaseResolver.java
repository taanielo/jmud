package io.taanielo.jmud.core.healing;

import java.util.Objects;

import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.player.Player;

/**
 * Resolves the base healing per tick for a player, incorporating race/class modifiers.
 */
public class HealingBaseResolver {

    /**
     * Calculates the base healing per tick for a player.
     */
    public int baseHpPerTick(Player player) {
        Objects.requireNonNull(player, "Player is required");
        int base = HealingSettings.baseHpPerTick();
        RaceId race = player.getRace();
        ClassId classId = player.getClassId();
        int raceModifier = HealingSettings.raceModifier(race);
        int classModifier = HealingSettings.classModifier(classId);
        int combined = base + raceModifier + classModifier;
        return Math.max(0, combined);
    }
}
