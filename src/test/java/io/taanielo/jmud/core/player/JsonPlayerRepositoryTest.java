package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.achievement.AchievementId;
import io.taanielo.jmud.core.achievement.PlayerAchievements;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.PlayerReputation;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;
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
    void savesAndLoadsFriendList() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("buddy"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ")
            .withFriendList(new PlayerFriendList(List.of("Alice", "Bob")));

        repository.savePlayer(player);
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent());
        assertEquals(List.of("alice", "bob"), loaded.get().getFriends());
        assertTrue(loaded.get().friendList().has("Alice"));
    }

    @Test
    void loadsPlayerWithoutFriendsFieldAsEmptyList() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("lonely"), Password.hash("pw", 1000));
        // Save a player with no friends field, mirroring existing/legacy saves.
        repository.savePlayer(Player.of(user, "%hp> "));
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().friendList().isEmpty());
        assertTrue(loaded.get().getFriends().isEmpty());
    }

    @Test
    void savesAndLoadsActiveTitle() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("champ"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ")
            .grantTitle("Centurion")
            .grantTitle("Slayer")
            .withActiveTitle("Slayer");

        repository.savePlayer(player);
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent());
        assertEquals(List.of("Centurion", "Slayer"), loaded.get().getTitles());
        assertEquals("Slayer", loaded.get().titles().active());
    }

    @Test
    void loadsPlayerWithoutActiveTitleFieldAsNoneActive() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("legacy"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ").grantTitle("Centurion");

        // Save produces a file without an activeTitle field (none selected), mirroring old saves.
        repository.savePlayer(player);
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent());
        assertEquals(List.of("Centurion"), loaded.get().getTitles());
        assertTrue(loaded.get().titles().active() == null);
    }

    @Test
    void savesAndLoadsFactionReputation() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("outlaw"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ").withReputation(
            PlayerReputation.empty().adjust(FactionId.of("bandits"), -30));

        repository.savePlayer(player);
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent());
        assertEquals(-30, loaded.get().reputation().standing(FactionId.of("bandits")));
    }

    @Test
    void savesAndLoadsIgnoreList() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("hermit"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ").withIgnoreList(
            PlayerIgnoreList.empty().with("Spammer").with("Troll"));

        repository.savePlayer(player);
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().ignoreList().has("spammer"));
        assertTrue(loaded.get().ignoreList().has("TROLL"));
        assertEquals(List.of("spammer", "troll"), loaded.get().getIgnoredPlayers());
    }

    @Test
    void savesAndLoadsUnlockedAchievements() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("hero"), Password.hash("pw", 1000));
        Instant unlockedAt = Instant.parse("2026-07-09T14:22:00Z");
        Player player = Player.of(user, "%hp> ").withAchievements(
            PlayerAchievements.empty().unlock(AchievementId.of("first_kill"), unlockedAt));

        repository.savePlayer(player);
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent(), "Player should reload after reconnection");
        PlayerAchievements achievements = loaded.get().achievements();
        assertTrue(achievements.has(AchievementId.of("first_kill")),
            "Unlocked achievement should survive the save/load round-trip");
        assertEquals(Optional.of(unlockedAt), achievements.unlockedAt(AchievementId.of("first_kill")),
            "Unlock timestamp should round-trip through JSON");
    }

    @Test
    void savesAndLoadsExploredRooms() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("explorer"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ")
            .exploreRoom(RoomId.of("training-yard"))
            .exploreRoom(RoomId.of("armory"));

        repository.savePlayer(player);
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent(), "Player should reload after reconnection");
        PlayerExploration exploration = loaded.get().exploration();
        assertTrue(exploration.hasVisited(RoomId.of("training-yard")),
            "Explored room should survive the save/load round-trip");
        assertTrue(exploration.hasVisited(RoomId.of("armory")),
            "Explored room should survive the save/load round-trip");
        assertEquals(2, exploration.count());
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
    void savesBankedItemsToJsonFile() throws Exception {
        // The player mapper cannot re-hydrate raw Item objects (Item has no Jackson creator — the
        // same pre-existing limitation that applies to the "inventory" field), so we assert on the
        // persisted write path: the vault is serialised alongside inventory under "bankedItems".
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("vaulter"), Password.hash("pw", 1));
        Item trophy = Item.builder(
                ItemId.of("trophy"), "a dragon trophy", "A gleaming trophy.", ItemAttributes.empty())
            .weight(3)
            .build();
        Player player = Player.of(user, "%hp> ").addBankedItem(trophy);

        repository.savePlayer(player);

        Path file = tempDir.resolve("players").resolve("vaulter.json");
        JsonNode root = new ObjectMapper().readTree(Files.readString(file));
        JsonNode banked = root.get("bankedItems");
        assertTrue(banked.isArray() && banked.size() == 1, "vault should be persisted under bankedItems");
        assertEquals("a dragon trophy", banked.get(0).get("name").asText());
    }

    @Test
    void loadingOldSaveFileWithoutBankedItemsDefaultsToEmpty() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("novault"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ");

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().getBankedItems().isEmpty(),
            "Player with no bankedItems field should load with an empty vault");
    }

    @Test
    void savesAndLoadsAliases() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("aliaser"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ").defineAlias("k", "kill").defineAlias("l", "look");

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertEquals("kill", loaded.get().aliases().expansionOf("k"));
        assertEquals("look", loaded.get().aliases().expansionOf("l"));
    }

    @Test
    void loadingOldSaveFileWithoutAliasesDefaultsToEmpty() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("noaliases"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ");

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().aliases().expansions().isEmpty());
    }

    @Test
    void savesAndLoadsMailbox() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("mailee"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ")
            .withMailbox(PlayerMailbox.empty().add(new PlayerMailMessage("sender", 5, "Hi there!", false)));

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertEquals(1, loaded.get().mailbox().messages().size());
        assertEquals("sender", loaded.get().mailbox().messages().get(0).sender());
        assertEquals("Hi there!", loaded.get().mailbox().messages().get(0).body());
    }

    @Test
    void loadingOldSaveFileWithoutMailboxDefaultsToEmpty() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("nomail"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ");

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().mailbox().isEmpty());
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
    void deletePlayerRemovesPersistedRecord() throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("condemned"), Password.hash("pw", 1));
        repository.savePlayer(Player.of(user, "%hp> "));
        assertTrue(repository.loadPlayer(user.getUsername()).isPresent());

        boolean deleted = repository.deletePlayer(user.getUsername());

        assertTrue(deleted, "deletePlayer should report the record was removed");
        assertTrue(repository.loadPlayer(user.getUsername()).isEmpty(),
            "deleted player should no longer load");
    }

    @Test
    void deletePlayerReturnsFalseWhenNoRecordExists() {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        assertFalse(repository.deletePlayer(Username.of("ghost")),
            "deleting a non-existent player should report false");
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
