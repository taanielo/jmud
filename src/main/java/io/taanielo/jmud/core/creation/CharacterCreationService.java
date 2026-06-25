package io.taanielo.jmud.core.creation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;

/**
 * Application service that drives the character-creation flow for new players.
 *
 * <p>The flow has two sequential steps:
 * <ol>
 *   <li>The player chooses a race from the list returned by {@link #buildRacePrompt()}.
 *   <li>The player chooses a class from the list returned by {@link #buildClassPrompt()}.
 * </ol>
 *
 * <p>Input is validated by {@link #resolveRace(String)} and {@link #resolveClass(String)};
 * both return an empty {@link Optional} for unrecognised input so the caller can
 * re-prompt.
 *
 * <p>This class is stateless and thread-safe; one instance may be shared
 * across all connected clients.
 */
public class CharacterCreationService {

    private final RaceRepository raceRepository;
    private final ClassRepository classRepository;

    /**
     * Constructs the service with the given repositories.
     *
     * @param raceRepository  repository used to look up available races
     * @param classRepository repository used to look up available classes
     */
    public CharacterCreationService(RaceRepository raceRepository, ClassRepository classRepository) {
        this.raceRepository = Objects.requireNonNull(raceRepository, "Race repository is required");
        this.classRepository = Objects.requireNonNull(classRepository, "Class repository is required");
    }

    /**
     * Builds the race-selection prompt text that is sent to the player.
     *
     * @return a multi-line prompt string listing all available races
     * @throws CharacterCreationException if race data cannot be loaded
     */
    public String buildRacePrompt() throws CharacterCreationException {
        List<Race> races = loadRaces();
        StringBuilder sb = new StringBuilder();
        sb.append("Choose your race:\n");
        for (Race race : races) {
            sb.append("  ").append(race.id().getValue())
              .append(" - ").append(race.name());
            appendRaceHint(sb, race);
            sb.append('\n');
        }
        sb.append("Enter race name: ");
        return sb.toString();
    }

    /**
     * Builds the class-selection prompt text that is sent to the player.
     *
     * @return a multi-line prompt string listing all available classes
     * @throws CharacterCreationException if class data cannot be loaded
     */
    public String buildClassPrompt() throws CharacterCreationException {
        List<ClassDefinition> classes = loadClasses();
        StringBuilder sb = new StringBuilder();
        sb.append("Choose your class:\n");
        for (ClassDefinition cd : classes) {
            sb.append("  ").append(cd.id().getValue())
              .append(" - ").append(cd.name());
            appendClassHint(sb, cd);
            sb.append('\n');
        }
        sb.append("Enter class name: ");
        return sb.toString();
    }

    /**
     * Resolves a player's race input to a {@link RaceId}.
     *
     * <p>Matching is case-insensitive on the race's id value.
     *
     * @param input the raw text typed by the player
     * @return the matching {@link RaceId}, or empty if input is unrecognised
     * @throws CharacterCreationException if race data cannot be loaded
     */
    public Optional<RaceId> resolveRace(String input) throws CharacterCreationException {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalised = input.trim().toLowerCase(Locale.ROOT);
        return loadRaces().stream()
            .filter(r -> r.id().getValue().equalsIgnoreCase(normalised)
                      || r.name().equalsIgnoreCase(normalised))
            .map(Race::id)
            .findFirst();
    }

    /**
     * Resolves a player's class input to a {@link ClassId}.
     *
     * <p>Matching is case-insensitive on the class's id value or name.
     *
     * @param input the raw text typed by the player
     * @return the matching {@link ClassId}, or empty if input is unrecognised
     * @throws CharacterCreationException if class data cannot be loaded
     */
    public Optional<ClassId> resolveClass(String input) throws CharacterCreationException {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalised = input.trim().toLowerCase(Locale.ROOT);
        return loadClasses().stream()
            .filter(c -> c.id().getValue().equalsIgnoreCase(normalised)
                      || c.name().equalsIgnoreCase(normalised))
            .map(ClassDefinition::id)
            .findFirst();
    }

    // ── private helpers ────────────────────────────────────────────────

    private List<Race> loadRaces() throws CharacterCreationException {
        try {
            List<Race> races = raceRepository.findAll();
            if (races.isEmpty()) {
                throw new CharacterCreationException("No races are defined in the data directory.");
            }
            return races.stream()
                .sorted((a, b) -> a.id().getValue().compareToIgnoreCase(b.id().getValue()))
                .toList();
        } catch (RaceRepositoryException e) {
            throw new CharacterCreationException("Failed to load races: " + e.getMessage(), e);
        }
    }

    private List<ClassDefinition> loadClasses() throws CharacterCreationException {
        try {
            List<ClassDefinition> classes = classRepository.findAll();
            if (classes.isEmpty()) {
                throw new CharacterCreationException("No classes are defined in the data directory.");
            }
            return classes.stream()
                .sorted((a, b) -> a.id().getValue().compareToIgnoreCase(b.id().getValue()))
                .toList();
        } catch (ClassRepositoryException e) {
            throw new CharacterCreationException("Failed to load classes: " + e.getMessage(), e);
        }
    }

    private void appendRaceHint(StringBuilder sb, Race race) {
        // Show carry capacity and healing modifier as brief description.
        sb.append(" (carry ").append(race.carryBase());
        int hmod = race.healingBaseModifier();
        if (hmod > 0) {
            sb.append(", healing +").append(hmod);
        } else if (hmod < 0) {
            sb.append(", healing ").append(hmod);
        }
        sb.append(')');
    }

    private void appendClassHint(StringBuilder sb, ClassDefinition cd) {
        sb.append(" (carry bonus +").append(cd.carryBonus());
        int hmod = cd.healingBaseModifier();
        if (hmod > 0) {
            sb.append(", healing +").append(hmod);
        } else if (hmod < 0) {
            sb.append(", healing ").append(hmod);
        }
        sb.append(')');
    }
}
