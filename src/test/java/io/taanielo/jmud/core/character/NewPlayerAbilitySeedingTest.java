package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.creation.CharacterCreationException;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.player.Player;

/**
 * Verifies that new-player ability seeding uses the chosen class's starting abilities,
 * and that the learned-ability gate blocks use of unlearned abilities.
 */
class NewPlayerAbilitySeedingTest {

    private static final AbilityId BASH = AbilityId.of("skill.bash");
    private static final AbilityId HEAL = AbilityId.of("spell.heal");
    private static final AbilityId FIREBALL = AbilityId.of("spell.fireball");

    private static final ClassDefinition WARRIOR = new ClassDefinition(
        ClassId.of("warrior"), "Warrior", 3, 20, List.of(BASH)
    );
    private static final ClassDefinition MAGE = new ClassDefinition(
        ClassId.of("mage"), "Mage", -1, 5, List.of(FIREBALL, HEAL)
    );
    private static final ClassDefinition ADVENTURER = new ClassDefinition(
        ClassId.of("adventurer"), "Adventurer", 0, 10, List.of(BASH, HEAL)
    );

    // ── CharacterCreationService.resolveClassDefinition ────────────────

    @Test
    void resolveClassDefinition_returnsFullDefinitionForWarrior()
        throws CharacterCreationException {
        CharacterCreationService svc = serviceWithClasses(WARRIOR, MAGE, ADVENTURER);

        Optional<ClassDefinition> result = svc.resolveClassDefinition("warrior");

        assertTrue(result.isPresent());
        assertEquals(List.of(BASH), result.get().startingAbilityIds());
    }

    @Test
    void resolveClassDefinition_caseInsensitive() throws CharacterCreationException {
        CharacterCreationService svc = serviceWithClasses(WARRIOR, MAGE, ADVENTURER);

        Optional<ClassDefinition> result = svc.resolveClassDefinition("MAGE");

        assertTrue(result.isPresent());
        assertTrue(result.get().startingAbilityIds().contains(FIREBALL));
        assertTrue(result.get().startingAbilityIds().contains(HEAL));
    }

    @Test
    void resolveClassDefinition_unknownInputReturnsEmpty() throws CharacterCreationException {
        CharacterCreationService svc = serviceWithClasses(WARRIOR, MAGE, ADVENTURER);

        assertTrue(svc.resolveClassDefinition("paladin").isEmpty());
    }

    @Test
    void resolveClassDefinition_blankInputReturnsEmpty() throws CharacterCreationException {
        CharacterCreationService svc = serviceWithClasses(WARRIOR, MAGE, ADVENTURER);

        assertTrue(svc.resolveClassDefinition("").isEmpty());
        assertTrue(svc.resolveClassDefinition(null).isEmpty());
    }

    // ── Player seeding ─────────────────────────────────────────────────

    @Test
    void newPlayerHasEmptyAbilitiesByDefault() {
        Player player = newPlayer("Alice");
        assertTrue(player.getLearnedAbilities().isEmpty(),
            "A freshly created player must start with no abilities");
    }

    @Test
    void playerSeedFromWarriorClassGivesBash() {
        Player player = newPlayer("Bob").withLearnedAbilities(WARRIOR.startingAbilityIds());
        assertEquals(List.of(BASH), player.getLearnedAbilities());
    }

    @Test
    void playerSeedFromMageClassGivesFireballAndHeal() {
        Player player = newPlayer("Cam").withLearnedAbilities(MAGE.startingAbilityIds());
        List<AbilityId> learned = player.getLearnedAbilities();
        assertTrue(learned.contains(FIREBALL));
        assertTrue(learned.contains(HEAL));
        assertEquals(2, learned.size());
    }

    @Test
    void playerSeedFromAdventurerClassGivesBashAndHeal() {
        Player player = newPlayer("Dan").withLearnedAbilities(ADVENTURER.startingAbilityIds());
        List<AbilityId> learned = player.getLearnedAbilities();
        assertTrue(learned.contains(BASH));
        assertTrue(learned.contains(HEAL));
        assertEquals(2, learned.size());
    }

    // ── Learned-ability gate via AbilityRegistry.findBestMatch ─────────

    @Test
    void abilityRegistryRejectsAbilitiesNotInLearnedList() {
        // We test via findBestMatch which is what AbilityEngine uses.
        AbilityRegistry registry = new AbilityRegistry(List.of());
        Optional<io.taanielo.jmud.core.ability.AbilityMatch> match =
            registry.findBestMatch("bash", List.of());
        assertTrue(match.isEmpty(),
            "findBestMatch with empty learned list must return empty");
    }

    @Test
    void abilityRegistryAllowsAbilitiesInLearnedList() {
        // Build a stub ability so findBestMatch can resolve it.
        io.taanielo.jmud.core.ability.Ability bash = stubAbility("skill.bash", "bash");
        AbilityRegistry registry = new AbilityRegistry(List.of(bash));

        Optional<io.taanielo.jmud.core.ability.AbilityMatch> match =
            registry.findBestMatch("bash", List.of(BASH));

        assertFalse(match.isEmpty(),
            "findBestMatch should find ability when it is in the learned list");
        assertEquals(BASH, match.get().ability().id());
    }

    @Test
    void abilityRegistryBlocksAbilityNotInLearnedList() {
        io.taanielo.jmud.core.ability.Ability bash = stubAbility("skill.bash", "bash");
        io.taanielo.jmud.core.ability.Ability fireball = stubAbility("spell.fireball", "fireball");
        AbilityRegistry registry = new AbilityRegistry(List.of(bash, fireball));

        // Player only knows bash, not fireball.
        Optional<io.taanielo.jmud.core.ability.AbilityMatch> match =
            registry.findBestMatch("fireball", List.of(BASH));

        assertTrue(match.isEmpty(),
            "findBestMatch must not match ability not in the learned list");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static Player newPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>", false, List.of());
    }

    private static CharacterCreationService serviceWithClasses(ClassDefinition... defs) {
        return new CharacterCreationService(
            new StubRaceRepository(),
            new StubClassRepository(List.of(defs))
        );
    }

    private static io.taanielo.jmud.core.ability.Ability stubAbility(String id, String name) {
        return new io.taanielo.jmud.core.ability.Ability() {
            @Override public AbilityId id() { return AbilityId.of(id); }
            @Override public String name() { return name; }
            @Override public io.taanielo.jmud.core.ability.AbilityType type() {
                return io.taanielo.jmud.core.ability.AbilityType.SKILL;
            }
            @Override public int level() { return 1; }
            @Override public io.taanielo.jmud.core.ability.AbilityCost cost() {
                return new io.taanielo.jmud.core.ability.AbilityCost(0, 0);
            }
            @Override public io.taanielo.jmud.core.ability.AbilityCooldown cooldown() {
                return new io.taanielo.jmud.core.ability.AbilityCooldown(0);
            }
            @Override public io.taanielo.jmud.core.ability.AbilityTargeting targeting() {
                return io.taanielo.jmud.core.ability.AbilityTargeting.HARMFUL;
            }
            @Override public List<String> aliases() { return List.of(); }
            @Override public List<io.taanielo.jmud.core.ability.AbilityEffect> effects() {
                return List.of();
            }
            @Override public List<io.taanielo.jmud.core.messaging.MessageSpec> messages() {
                return List.of();
            }
        };
    }

    private static class StubRaceRepository implements RaceRepository {
        @Override public Optional<Race> findById(RaceId id) { return Optional.empty(); }
        @Override public List<Race> findAll() { return List.of(); }
    }

    private static class StubClassRepository implements ClassRepository {
        private final List<ClassDefinition> classes;

        StubClassRepository(List<ClassDefinition> classes) {
            this.classes = classes;
        }

        @Override
        public Optional<ClassDefinition> findById(ClassId id) {
            return classes.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<ClassDefinition> findAll() {
            return classes;
        }
    }
}
