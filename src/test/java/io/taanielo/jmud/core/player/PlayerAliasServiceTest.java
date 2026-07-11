package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for {@link PlayerAliasService}.
 */
class PlayerAliasServiceTest {

    private final PlayerAliasService service = new PlayerAliasService();

    private static Player stubPlayer() {
        User user = User.of(Username.of("hero"), Password.hash("secret"));
        return Player.of(user, "%hp> ");
    }

    @Test
    void definesNewAlias() {
        Player player = stubPlayer();
        AliasResult result = service.define(player, "k", "kill", Set.of());

        assertTrue(result.success());
        assertTrue(result.message().contains("defined"));
        assertEquals("kill", result.updatedPlayer().aliases().expansionOf("k"));
    }

    @Test
    void rejectsSelfReferencingAlias() {
        Player player = stubPlayer();
        AliasResult result = service.define(player, "k", "k rat", Set.of());

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
        assertTrue(result.message().toLowerCase(Locale.ROOT).contains("itself"));
    }

    @Test
    void warnsWhenAliasShadowsBuiltinCommand() {
        Player player = stubPlayer();
        AliasResult result = service.define(player, "kill", "attack rat", Set.of("kill", "look"));

        assertTrue(result.success());
        assertTrue(result.message().toLowerCase(Locale.ROOT).contains("warning"));
        assertEquals("attack rat", result.updatedPlayer().aliases().expansionOf("kill"));
    }

    @Test
    void removesExistingAlias() {
        Player player = stubPlayer().defineAlias("k", "kill");
        AliasResult result = service.remove(player, "k");

        assertTrue(result.success());
        assertFalse(result.updatedPlayer().aliases().has("k"));
    }

    @Test
    void removingUnknownAliasFails() {
        Player player = stubPlayer();
        AliasResult result = service.remove(player, "missing");

        assertFalse(result.success());
        assertNull(result.updatedPlayer());
    }

    @Test
    void listsDefinedAliases() {
        Player player = stubPlayer().defineAlias("k", "kill").defineAlias("l", "look");
        AliasResult result = service.list(player);

        assertTrue(result.success());
        List<String> lines = result.lines();
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(l -> l.contains("k -> kill")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("l -> look")));
    }

    @Test
    void listingWithNoAliasesFailsWithHelpfulMessage() {
        Player player = stubPlayer();
        AliasResult result = service.list(player);

        assertFalse(result.success());
        assertTrue(result.lines().isEmpty());
        assertTrue(result.message().toLowerCase(Locale.ROOT).contains("no aliases"));
    }
}
