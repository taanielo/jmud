package io.taanielo.jmud.core.creation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

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

    /**
     * Practice points granted to a brand-new character so the Master Trainer is usable
     * on day one. Practice points are otherwise only earned by levelling up, which left
     * a fresh character unable to TRAIN anything until after surviving the early fights.
     */
    public static final int STARTING_PRACTICE_POINTS = 2;

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
        sb.append("Choose your race:\r\n");
        for (Race race : races) {
            sb.append("  ").append(race.id().getValue())
              .append(" - ").append(race.name());
            appendRaceHint(sb, race);
            sb.append("\r\n");
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
        sb.append("Choose your class:\r\n");
        for (ClassDefinition cd : classes) {
            sb.append("  ").append(cd.id().getValue())
              .append(" - ").append(cd.name());
            appendClassHint(sb, cd);
            sb.append("\r\n");
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
        return resolveClassDefinition(input).map(ClassDefinition::id);
    }

    /**
     * Resolves a player's class input to the full {@link ClassDefinition}.
     *
     * <p>Matching is case-insensitive on the class's id value or name.
     *
     * @param input the raw text typed by the player
     * @return the matching {@link ClassDefinition}, or empty if input is unrecognised
     * @throws CharacterCreationException if class data cannot be loaded
     */
    public Optional<ClassDefinition> resolveClassDefinition(String input) throws CharacterCreationException {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String normalised = input.trim().toLowerCase(Locale.ROOT);
        return loadClasses().stream()
            .filter(c -> c.id().getValue().equalsIgnoreCase(normalised)
                      || c.name().equalsIgnoreCase(normalised))
            .findFirst();
    }

    /**
     * Applies the chosen race's starting stat modifiers to a freshly created player.
     *
     * <p>Currently this adjusts the player's maximum mana pool by the race's
     * {@link Race#manaModifier()} (e.g. Elves gain a deeper pool, Orcs a shallower one).
     * The mana pool is left full after the adjustment so a new character begins ready to
     * cast. The maximum is clamped to a minimum of {@code 1} so a large negative modifier
     * can never invalidate the vitals. Other race stats (carry capacity, healing, armor,
     * attack) are resolved at their point of use and are not baked into the player here.
     *
     * @param player the newly created player whose stats should be adjusted
     * @param raceId the race chosen during character creation; {@code null} leaves the player unchanged
     * @return the player with race starting stats applied, or the original player when the
     *         race is {@code null} or unknown
     * @throws CharacterCreationException if race data cannot be loaded
     */
    public Player applyRaceStartingStats(Player player, RaceId raceId) throws CharacterCreationException {
        Objects.requireNonNull(player, "Player is required");
        if (raceId == null) {
            return player;
        }
        Race race;
        try {
            race = raceRepository.findById(raceId).orElse(null);
        } catch (RaceRepositoryException e) {
            throw new CharacterCreationException("Failed to load race " + raceId.getValue() + ": " + e.getMessage(), e);
        }
        if (race == null || race.manaModifier() == 0) {
            return player;
        }
        PlayerVitals vitals = player.getVitals();
        int newMaxMana = Math.max(1, vitals.maxMana() + race.manaModifier());
        PlayerVitals adjusted = vitals.withMaxMana(newMaxMana);
        if (newMaxMana > adjusted.mana()) {
            adjusted = adjusted.restoreMana(newMaxMana - adjusted.mana());
        }
        return player.withVitals(adjusted);
    }

    /**
     * Applies the chosen class's starting state to a freshly created player: it grants the
     * class's starting (auto-learned) abilities and seeds {@link #STARTING_PRACTICE_POINTS}
     * practice points so the player can immediately TRAIN advanced abilities at the Master
     * Trainer.
     *
     * <p>The class's {@link ClassDefinition#trainableAbilityIds() trainable abilities} are
     * deliberately <em>not</em> learned here — they are the day-one training options.
     *
     * @param player   the newly created player whose class state should be applied
     * @param classDef the class chosen during character creation; {@code null} leaves the player unchanged
     * @return the player with starting abilities learned and starting practice points granted,
     *         or the original player when {@code classDef} is {@code null}
     */
    public Player applyClassStartingState(Player player, ClassDefinition classDef) {
        Objects.requireNonNull(player, "Player is required");
        if (classDef == null) {
            return player;
        }
        Player result = player;
        List<AbilityId> startingAbilities = classDef.startingAbilityIds();
        if (!startingAbilities.isEmpty()) {
            result = result.withLearnedAbilities(startingAbilities);
        }
        return result.withPracticePoints(STARTING_PRACTICE_POINTS);
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
        // Show carry capacity, healing, mana and attack modifiers as a brief description.
        sb.append(" (carry ").append(race.carryBase());
        appendSignedModifier(sb, "healing", race.healingBaseModifier());
        appendSignedModifier(sb, "mana", race.manaModifier());
        appendSignedModifier(sb, "attack", race.attackModifier());
        sb.append(')');
    }

    private void appendSignedModifier(StringBuilder sb, String label, int value) {
        if (value > 0) {
            sb.append(", ").append(label).append(" +").append(value);
        } else if (value < 0) {
            sb.append(", ").append(label).append(' ').append(value);
        }
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
