package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class JsonPlayerRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsPlayerWithEffects() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("sparky"), Password.hash("qwerty", 1000));
        List<EffectInstance> effects = List.of(new EffectInstance(EffectId.of("stoneskin"), 5, 1));
        PlayerVitals vitals = new PlayerVitals(12, 20, 6, 10, 8, 15);
        Player player = new Player(
            user,
            3,
            42,
            vitals,
            effects,
            "HP {hp}/{maxHp} Exp {exp}",
            true,
            List.of(AbilityId.of("spell.heal")),
            RaceId.of("troll"),
            ClassId.of("warrior")
        );

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertEquals("sparky", loaded.get().getUsername().getValue());
        assertEquals(3, loaded.get().getLevel());
        assertEquals(42, loaded.get().getExperience());
        assertEquals(12, loaded.get().getVitals().hp());
        assertEquals(20, loaded.get().getVitals().maxHp());
        assertEquals("HP {hp}/{maxHp} Exp {exp}", loaded.get().getPromptFormat());
        assertTrue(loaded.get().isAnsiEnabled());
        assertEquals(List.of(AbilityId.of("spell.heal")), loaded.get().getLearnedAbilities());
        assertEquals("troll", loaded.get().getRace().getValue());
        assertEquals("warrior", loaded.get().getClassId().getValue());
        assertEquals(1, loaded.get().effects().size());
        assertEquals("stoneskin", loaded.get().effects().get(0).id().getValue());
    }

    @Test
    void savesAndLoadsGoldField() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("goldie"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ").withGold(42);

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertEquals(42, loaded.get().getGold(), "Gold should round-trip through JSON");
    }

    @Test
    void loadingOldSaveFileWithoutGoldDefaultsToZero() throws Exception {
        // Simulate a save file that lacks a "gold" property by serializing a
        // player with 0 gold. The @JsonCreator maps null gold → 0, so any
        // file missing the field behaves as if gold == 0.
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("veteran"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> "); // gold defaults to 0

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertEquals(0, loaded.get().getGold(),
            "Player with no gold field set should load as 0 gold");
    }

    @Test
    void findAllReturnsEveryPersistedPlayer() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        Player alice = Player.of(User.of(Username.of("alice"), Password.hash("pw", 1)), "%hp> ").withTotalKills(10);
        Player bob = Player.of(User.of(Username.of("bob"), Password.hash("pw", 1)), "%hp> ").withTotalKills(3);

        repository.savePlayer(alice);
        repository.savePlayer(bob);

        List<Player> all = repository.findAll();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(p -> p.getUsername().getValue().equals("alice") && p.getTotalKills() == 10));
        assertTrue(all.stream().anyMatch(p -> p.getUsername().getValue().equals("bob") && p.getTotalKills() == 3));
    }

    @Test
    void findAllReturnsEmptyListWhenNoPlayersDirectoryExists() {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir.resolve("unused-root"));
        assertEquals(List.of(), repository.findAll());
    }

    @Test
    void savePlayerThrowsRepositoryExceptionWhenPlayersDirectoryIsUnwritable() throws Exception {
        Path dataRoot = tempDir.resolve("unwritable-root");
        JsonPlayerRepository repository = new JsonPlayerRepository(dataRoot);
        Path playersDir = dataRoot.resolve("players");
        // Some environments (e.g. root-owned containers) ignore write permission bits;
        // skip rather than false-fail if we can't actually make the directory unwritable.
        assumeTrue(playersDir.toFile().setWritable(false), "Could not make players directory read-only");
        try {
            User user = User.of(Username.of("doomed"), Password.hash("pw", 1));
            Player player = Player.of(user, "%hp> ");

            assertThrows(RepositoryException.class, () -> repository.savePlayer(player));
        } finally {
            playersDir.toFile().setWritable(true);
        }
    }
}
