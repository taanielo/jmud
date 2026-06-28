package io.taanielo.jmud.core.combat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.player.Player;

/**
 * Resolves the natural armor bonus granted by a player's race, which reduces
 * attacker hit chance in combat.
 */
public class RaceArmorBonusResolver {
    private final RaceRepository raceRepository;

    /**
     * Creates a resolver backed by the provided race repository.
     */
    public RaceArmorBonusResolver(RaceRepository raceRepository) {
        this.raceRepository = Objects.requireNonNull(raceRepository, "Race repository is required");
    }

    /**
     * Returns the armor bonus for the given player's race.
     * Returns {@code 0} if the player has no race assigned or the race is not found.
     */
    public int armorBonus(Player player) {
        Objects.requireNonNull(player, "Player is required");
        RaceId raceId = player.getRace();
        if (raceId == null) {
            return 0;
        }
        try {
            Race race = raceRepository.findById(raceId).orElse(null);
            if (race == null) {
                return 0;
            }
            return race.armorBonus();
        } catch (RaceRepositoryException e) {
            throw new IllegalStateException("Failed to resolve race " + raceId.getValue(), e);
        }
    }

    /**
     * Returns a no-op resolver that always contributes zero armor bonus.
     * Intended for use in legacy constructors or test contexts where race data is unavailable.
     */
    public static RaceArmorBonusResolver noOp() {
        RaceRepository emptyRepo = new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId id) {
                return Optional.empty();
            }

            @Override
            public List<Race> findAll() {
                return List.of();
            }
        };
        return new RaceArmorBonusResolver(emptyRepo);
    }
}
