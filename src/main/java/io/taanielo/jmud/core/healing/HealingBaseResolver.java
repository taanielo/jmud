package io.taanielo.jmud.core.healing;

import java.util.Objects;

import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.player.Player;

/**
 * Resolves the base healing per tick for a player, incorporating race/class modifiers.
 */
public class HealingBaseResolver {
    private final RaceRepository raceRepository;

    public HealingBaseResolver(RaceRepository raceRepository) {
        this.raceRepository = Objects.requireNonNull(raceRepository, "Race repository is required");
    }

    /**
     * Calculates the base healing per tick for a player.
     */
    public int baseHpPerTick(Player player) {
        Objects.requireNonNull(player, "Player is required");
        int base = HealingSettings.baseHpPerTick();
        RaceId race = player.getRace();
        int raceModifier = resolveRaceModifier(race);
        int classModifier = HealingSettings.classModifier(player.getClassId());
        int combined = base + raceModifier + classModifier;
        return Math.max(0, combined);
    }

    private int resolveRaceModifier(RaceId raceId) {
        if (raceId == null) {
            return 0;
        }
        try {
            Race race = raceRepository.findById(raceId).orElse(null);
            if (race == null) {
                return 0;
            }
            return race.healingBaseModifier();
        } catch (RaceRepositoryException e) {
            throw new IllegalStateException("Failed to resolve race " + raceId.getValue(), e);
        }
    }
}
