package io.taanielo.jmud.core.combat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.player.Player;

/**
 * Resolves the natural armour bonus granted by a player's class, which reduces
 * attacker hit chance in combat.
 *
 * <p>Represents a class's armour proficiency (for example a Paladin's heavy armour
 * training). The resolved value stacks with the racial bonus from
 * {@link RaceArmorBonusResolver} and equipped-item AC from
 * {@link EquipmentArmorResolver}.
 */
public class ClassArmorBonusResolver {
    private final ClassRepository classRepository;

    /**
     * Creates a resolver backed by the provided class repository.
     */
    public ClassArmorBonusResolver(ClassRepository classRepository) {
        this.classRepository = Objects.requireNonNull(classRepository, "Class repository is required");
    }

    /**
     * Returns the armour bonus for the given player's class.
     * Returns {@code 0} if the player has no class assigned or the class is not found.
     */
    public int armorBonus(Player player) {
        Objects.requireNonNull(player, "Player is required");
        ClassId classId = player.getClassId();
        if (classId == null) {
            return 0;
        }
        try {
            ClassDefinition definition = classRepository.findById(classId).orElse(null);
            if (definition == null) {
                return 0;
            }
            return definition.armorBonus();
        } catch (ClassRepositoryException e) {
            throw new IllegalStateException("Failed to resolve class " + classId.getValue(), e);
        }
    }

    /**
     * Returns a no-op resolver that always contributes zero armour bonus.
     * Intended for use in legacy constructors or test contexts where class data is unavailable.
     */
    public static ClassArmorBonusResolver noOp() {
        ClassRepository emptyRepo = new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId id) {
                return Optional.empty();
            }

            @Override
            public List<ClassDefinition> findAll() {
                return List.of();
            }
        };
        return new ClassArmorBonusResolver(emptyRepo);
    }
}
