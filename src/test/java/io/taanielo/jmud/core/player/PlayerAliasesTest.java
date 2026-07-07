package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerAliases}.
 */
class PlayerAliasesTest {

    @Test
    void emptyHasNoAliases() {
        PlayerAliases aliases = PlayerAliases.empty();
        assertTrue(aliases.expansions().isEmpty());
        assertFalse(aliases.has("k"));
        assertNull(aliases.expansionOf("k"));
    }

    @Test
    void defineAddsAlias() {
        PlayerAliases aliases = PlayerAliases.empty().define("k", "kill");
        assertTrue(aliases.has("k"));
        assertEquals("kill", aliases.expansionOf("k"));
    }

    @Test
    void aliasLookupIsCaseInsensitive() {
        PlayerAliases aliases = PlayerAliases.empty().define("K", "kill");
        assertTrue(aliases.has("k"));
        assertEquals("kill", aliases.expansionOf("K"));
        assertEquals("kill", aliases.expansionOf("k"));
    }

    @Test
    void defineOverwritesExistingAlias() {
        PlayerAliases aliases = PlayerAliases.empty().define("k", "kill").define("k", "attack");
        assertEquals("attack", aliases.expansionOf("k"));
        assertEquals(1, aliases.expansions().size());
    }

    @Test
    void removeDeletesAlias() {
        PlayerAliases aliases = PlayerAliases.empty().define("k", "kill").remove("k");
        assertFalse(aliases.has("k"));
    }

    @Test
    void removeIsNoOpWhenAliasDoesNotExist() {
        PlayerAliases aliases = PlayerAliases.empty();
        assertTrue(aliases.remove("missing").expansions().isEmpty());
    }

    @Test
    void constructorNormalisesKeyCase() {
        PlayerAliases aliases = new PlayerAliases(Map.of("K", "kill"));
        assertTrue(aliases.has("k"));
    }
}
