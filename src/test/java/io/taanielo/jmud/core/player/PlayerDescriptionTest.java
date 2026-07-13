package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for the custom LOOK description carried on {@link Player}/{@link PlayerIdentity}.
 */
class PlayerDescriptionTest {

    private static Player newPlayer() {
        User user = User.of(Username.of("thorin"), Password.hash("pw", 1000));
        return Player.of(user, "%hp> ");
    }

    @Test
    void defaultsToEmptyDescription() {
        Player player = newPlayer();
        assertTrue(player.description().isEmpty());
        assertNull(player.getDescription(), "empty description should serialise as null (field omitted)");
    }

    @Test
    void setsAndTrimsDescription() {
        Player player = newPlayer().withDescription("  A grizzled dwarf with a notched axe.  ");
        assertEquals("A grizzled dwarf with a notched axe.", player.description());
        assertEquals("A grizzled dwarf with a notched axe.", player.getDescription());
    }

    @Test
    void clearsDescriptionBackToDefault() {
        Player player = newPlayer().withDescription("A grizzled dwarf.").withDescription("");
        assertTrue(player.description().isEmpty());
        assertNull(player.getDescription());
    }

    @Test
    void nullDescriptionIsTreatedAsCleared() {
        Player player = newPlayer().withDescription(null);
        assertTrue(player.description().isEmpty());
    }

    @Test
    void preservesDescriptionThroughIdentityChanges() {
        PlayerIdentity identity = newPlayer().identity().withDescription("A scarred veteran.");
        assertEquals("A scarred veteran.", identity.withLevel(20).description());
        assertEquals("A scarred veteran.", identity.withExperience(999).description());
    }
}
