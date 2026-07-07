package io.taanielo.jmud.core.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.RaceRepository;

/**
 * Unit tests for {@link CharacterCreationService} using stub repositories.
 */
class CharacterCreationServiceTest {

    private static final Race HUMAN = new Race(RaceId.of("human"), "Human", 0, 50);
    private static final Race ELF = new Race(RaceId.of("elf"), "Elf", -2, 40);
    private static final Race TROLL = new Race(RaceId.of("troll"), "Troll", 2, 80);

    private static final ClassDefinition WARRIOR = new ClassDefinition(ClassId.of("warrior"), "Warrior", 3, 20);
    private static final ClassDefinition MAGE = new ClassDefinition(ClassId.of("mage"), "Mage", -1, 5);
    private static final ClassDefinition ADVENTURER = new ClassDefinition(ClassId.of("adventurer"), "Adventurer", 0, 10);

    private CharacterCreationService service;

    @BeforeEach
    void setUp() {
        service = new CharacterCreationService(new StubRaceRepository(), new StubClassRepository());
    }

    // ── resolveRace ────────────────────────────────────────────────────

    @Test
    void resolveRace_byId_exactMatch() throws CharacterCreationException {
        Optional<RaceId> result = service.resolveRace("human");
        assertTrue(result.isPresent());
        assertEquals("human", result.get().getValue());
    }

    @Test
    void resolveRace_byId_caseInsensitive() throws CharacterCreationException {
        Optional<RaceId> result = service.resolveRace("ELF");
        assertTrue(result.isPresent());
        assertEquals("elf", result.get().getValue());
    }

    @Test
    void resolveRace_byName() throws CharacterCreationException {
        Optional<RaceId> result = service.resolveRace("Troll");
        assertTrue(result.isPresent());
        assertEquals("troll", result.get().getValue());
    }

    @Test
    void resolveRace_unknown_returnsEmpty() throws CharacterCreationException {
        Optional<RaceId> result = service.resolveRace("gnome");
        assertFalse(result.isPresent());
    }

    @Test
    void resolveRace_blank_returnsEmpty() throws CharacterCreationException {
        assertFalse(service.resolveRace("").isPresent());
        assertFalse(service.resolveRace("   ").isPresent());
        assertFalse(service.resolveRace(null).isPresent());
    }

    // ── resolveClass ───────────────────────────────────────────────────

    @Test
    void resolveClass_byId_exactMatch() throws CharacterCreationException {
        Optional<ClassId> result = service.resolveClass("warrior");
        assertTrue(result.isPresent());
        assertEquals("warrior", result.get().getValue());
    }

    @Test
    void resolveClass_byId_caseInsensitive() throws CharacterCreationException {
        Optional<ClassId> result = service.resolveClass("MAGE");
        assertTrue(result.isPresent());
        assertEquals("mage", result.get().getValue());
    }

    @Test
    void resolveClass_byName() throws CharacterCreationException {
        Optional<ClassId> result = service.resolveClass("Adventurer");
        assertTrue(result.isPresent());
        assertEquals("adventurer", result.get().getValue());
    }

    @Test
    void resolveClass_unknown_returnsEmpty() throws CharacterCreationException {
        assertFalse(service.resolveClass("druid").isPresent());
    }

    // ── prompt content ─────────────────────────────────────────────────

    @Test
    void buildRacePrompt_containsAllRaceIds() throws CharacterCreationException {
        String prompt = service.buildRacePrompt();
        assertTrue(prompt.contains("human"), "Prompt should contain 'human'");
        assertTrue(prompt.contains("elf"), "Prompt should contain 'elf'");
        assertTrue(prompt.contains("troll"), "Prompt should contain 'troll'");
    }

    @Test
    void buildClassPrompt_containsAllClassIds() throws CharacterCreationException {
        String prompt = service.buildClassPrompt();
        assertTrue(prompt.contains("warrior"), "Prompt should contain 'warrior'");
        assertTrue(prompt.contains("mage"), "Prompt should contain 'mage'");
        assertTrue(prompt.contains("adventurer"), "Prompt should contain 'adventurer'");
    }

    @Test
    void buildRacePrompt_rendersAsSeparateBoundedLines() throws CharacterCreationException {
        String prompt = service.buildRacePrompt();

        // Every line break must be a full CR+LF pair, matching the telnet protocol
        // convention used by MessageWriter#writeLine; a bare '\n' renders as a
        // "staircase" on real terminals instead of a clean list.
        assertFalse(prompt.replace("\r\n", "").contains("\n"),
            "Prompt must not contain bare '\\n' line breaks");

        String[] lines = prompt.split("\r\n");
        assertEquals(5, lines.length, "Expected a header line, three race lines, and the input prompt");
        assertEquals("Choose your race:", lines[0]);
        assertTrue(lines[1].contains("elf"));
        assertTrue(lines[2].contains("human"));
        assertTrue(lines[3].contains("troll"));
        assertEquals("Enter race name: ", lines[4]);
        for (String line : lines) {
            assertTrue(line.length() < 80, "Each rendered line should fit a normal terminal width: " + line);
        }
    }

    @Test
    void buildClassPrompt_rendersAsSeparateBoundedLines() throws CharacterCreationException {
        String prompt = service.buildClassPrompt();

        assertFalse(prompt.replace("\r\n", "").contains("\n"),
            "Prompt must not contain bare '\\n' line breaks");

        String[] lines = prompt.split("\r\n");
        assertEquals(5, lines.length, "Expected a header line, three class lines, and the input prompt");
        assertEquals("Choose your class:", lines[0]);
        assertTrue(lines[1].contains("adventurer"));
        assertTrue(lines[2].contains("mage"));
        assertTrue(lines[3].contains("warrior"));
        assertEquals("Enter class name: ", lines[4]);
        for (String line : lines) {
            assertTrue(line.length() < 80, "Each rendered line should fit a normal terminal width: " + line);
        }
    }

    @Test
    void buildRacePrompt_throwsWhenNoRaces() {
        CharacterCreationService emptyService = new CharacterCreationService(
            new EmptyRaceRepository(), new StubClassRepository()
        );
        assertThrows(CharacterCreationException.class, emptyService::buildRacePrompt);
    }

    @Test
    void buildClassPrompt_throwsWhenNoClasses() {
        CharacterCreationService emptyService = new CharacterCreationService(
            new StubRaceRepository(), new EmptyClassRepository()
        );
        assertThrows(CharacterCreationException.class, emptyService::buildClassPrompt);
    }

    // ── stub repositories ──────────────────────────────────────────────

    private static class StubRaceRepository implements RaceRepository {
        private final List<Race> races = List.of(HUMAN, ELF, TROLL);

        @Override
        public Optional<Race> findById(RaceId id) {
            return races.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<Race> findAll() {
            return races;
        }
    }

    private static class StubClassRepository implements ClassRepository {
        private final List<ClassDefinition> classes = List.of(WARRIOR, MAGE, ADVENTURER);

        @Override
        public Optional<ClassDefinition> findById(ClassId id) {
            return classes.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<ClassDefinition> findAll() {
            return classes;
        }
    }

    private static class EmptyRaceRepository implements RaceRepository {
        @Override
        public Optional<Race> findById(RaceId id) { return Optional.empty(); }

        @Override
        public List<Race> findAll() { return List.of(); }
    }

    private static class EmptyClassRepository implements ClassRepository {
        @Override
        public Optional<ClassDefinition> findById(ClassId id) { return Optional.empty(); }

        @Override
        public List<ClassDefinition> findAll() { return List.of(); }
    }
}
