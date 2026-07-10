package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.guild.GuildId;

class PlayerGuildMembershipTest {

    @Test
    void newPlayerHasNoGuild() {
        Player player = Player.of(User.of(Username.of("nova"), Password.hash("pw", 1000)), "%hp> ");

        assertFalse(player.guildMembership().hasGuild());
        assertEquals(null, player.getGuildId());
    }

    @Test
    void withGuildMembershipRecordsGuildId() {
        Player player = Player.of(User.of(Username.of("nova"), Password.hash("pw", 1000)), "%hp> ")
            .withGuildMembership(PlayerGuildMembership.of(GuildId.of("guild-42")));

        assertTrue(player.guildMembership().hasGuild());
        assertEquals("guild-42", player.getGuildId());
    }

    @Test
    void guildMembershipSurvivesPersistenceRoundTrip(@TempDir Path dataRoot) throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(dataRoot);
        User user = User.of(Username.of("nova"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ")
            .withGuildMembership(PlayerGuildMembership.of(GuildId.of("guild-42")));

        repository.savePlayer(player.snapshotForPersistence());
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().guildMembership().hasGuild());
        assertEquals("guild-42", loaded.get().getGuildId());
    }

    @Test
    void guildlessPlayerRoundTripsWithoutGuild(@TempDir Path dataRoot) throws Exception {
        JsonPlayerRepository repository = new JsonPlayerRepository(dataRoot);
        User user = User.of(Username.of("drifter"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ");

        repository.savePlayer(player.snapshotForPersistence());
        Optional<Player> loaded = repository.loadPlayer(user.getUsername());

        assertTrue(loaded.isPresent());
        assertFalse(loaded.get().guildMembership().hasGuild());
    }
}
