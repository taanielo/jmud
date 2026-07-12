package io.taanielo.jmud.core.character;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Resolves a character's derived {@link CharacterAttributes} from a snapshot of race and class
 * definitions captured once at construction time.
 *
 * <p>Attributes are never rolled or persisted: they are computed deterministically as
 * {@code baseline + race bonus + class creation bonus + class level schedule(level)}. Because the
 * lookup is a plain in-memory map, resolving attributes on the tick thread (during combat or a
 * spell) never touches a repository or disk (AGENTS.md §5). Build one resolver at bootstrap from the
 * already-loaded, cached race and class definitions.
 *
 * <p>Unknown or {@code null} race/class ids contribute no bonus, so a character with no recognised
 * race and class resolves to the all-baseline attribute set.
 */
public final class CharacterAttributesResolver {

    private final Map<RaceId, AttributeBonus> raceBonuses;
    private final Map<ClassId, AttributeBonus> classBonuses;
    private final Map<ClassId, AttributeGainSchedule> classSchedules;

    private CharacterAttributesResolver(
        Map<RaceId, AttributeBonus> raceBonuses,
        Map<ClassId, AttributeBonus> classBonuses,
        Map<ClassId, AttributeGainSchedule> classSchedules
    ) {
        this.raceBonuses = raceBonuses;
        this.classBonuses = classBonuses;
        this.classSchedules = classSchedules;
    }

    /**
     * Builds a resolver from snapshots of race and class definitions.
     *
     * @param races   the race definitions to index; must not be null
     * @param classes the class definitions to index; must not be null
     * @return a resolver backed by immutable snapshots of the given definitions
     */
    public static CharacterAttributesResolver fromDefinitions(
        Iterable<Race> races,
        Iterable<ClassDefinition> classes
    ) {
        Objects.requireNonNull(races, "races must not be null");
        Objects.requireNonNull(classes, "classes must not be null");
        Map<RaceId, AttributeBonus> raceMap = new HashMap<>();
        for (Race race : races) {
            raceMap.put(race.id(), race.attributeBonus());
        }
        Map<ClassId, AttributeBonus> classMap = new HashMap<>();
        Map<ClassId, AttributeGainSchedule> scheduleMap = new HashMap<>();
        for (ClassDefinition definition : classes) {
            classMap.put(definition.id(), definition.attributeBonus());
            scheduleMap.put(definition.id(), definition.attributeGains());
        }
        return new CharacterAttributesResolver(
            Map.copyOf(raceMap), Map.copyOf(classMap), Map.copyOf(scheduleMap));
    }

    /**
     * Returns a resolver that always yields the all-baseline attribute set. Intended for legacy or
     * test contexts where race and class data is unavailable.
     *
     * @return a resolver that ignores race/class/level and returns baseline attributes
     */
    public static CharacterAttributesResolver baselineOnly() {
        return new CharacterAttributesResolver(Map.of(), Map.of(), Map.of());
    }

    /**
     * Resolves the derived attributes for a character of the given race, class and level.
     *
     * @param raceId  the character's race id, or {@code null} for no race
     * @param classId the character's class id, or {@code null} for no class
     * @param level   the character's current level (1-based)
     * @return the derived attributes, never {@code null}
     */
    public CharacterAttributes resolve(@Nullable RaceId raceId, @Nullable ClassId classId, int level) {
        AttributeBonus raceBonus = raceId == null
            ? AttributeBonus.NONE
            : raceBonuses.getOrDefault(raceId, AttributeBonus.NONE);
        AttributeBonus classBonus = classId == null
            ? AttributeBonus.NONE
            : classBonuses.getOrDefault(classId, AttributeBonus.NONE);
        AttributeGainSchedule schedule = classId == null
            ? AttributeGainSchedule.NONE
            : classSchedules.getOrDefault(classId, AttributeGainSchedule.NONE);
        AttributeBonus total = raceBonus.plus(classBonus).plus(schedule.gainsAtLevel(level));
        return CharacterAttributes.baseline().plus(total);
    }
}
