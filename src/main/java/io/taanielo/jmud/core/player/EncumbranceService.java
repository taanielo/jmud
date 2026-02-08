package io.taanielo.jmud.core.player;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.world.Item;

/**
 * Resolves carry weight limits and encumbrance for a player.
 */
public class EncumbranceService {
    private final RaceRepository raceRepository;
    private final ClassRepository classRepository;

    /**
     * Creates an encumbrance service with the given repositories.
     */
    public EncumbranceService(RaceRepository raceRepository, ClassRepository classRepository) {
        this.raceRepository = Objects.requireNonNull(raceRepository, "Race repository is required");
        this.classRepository = Objects.requireNonNull(classRepository, "Class repository is required");
    }

    /**
     * Returns the maximum carry weight for the given player.
     */
    public int maxCarry(Player player) {
        Objects.requireNonNull(player, "Player is required");
        int base = 0;
        int bonus = 0;
        if (player.getRace() != null) {
            try {
                Race race = raceRepository.findById(player.getRace()).orElse(null);
                if (race != null) {
                    base = race.carryBase();
                }
            } catch (RaceRepositoryException ignored) {
            }
        }
        if (player.getClassId() != null) {
            try {
                ClassDefinition classDefinition = classRepository.findById(player.getClassId()).orElse(null);
                if (classDefinition != null) {
                    bonus = classDefinition.carryBonus();
                }
            } catch (ClassRepositoryException ignored) {
            }
        }
        return Math.max(0, base + bonus);
    }

    /**
     * Returns the total carried weight for the given player.
     */
    public int carriedWeight(Player player) {
        Objects.requireNonNull(player, "Player is required");
        List<Item> items = player.getInventory();
        int total = 0;
        for (Item item : items) {
            total += item.getWeight();
        }
        return total;
    }

    /**
     * Returns true if the player is over their maximum carry weight.
     */
    public boolean isOverburdened(Player player) {
        return carriedWeight(player) > maxCarry(player);
    }
}
