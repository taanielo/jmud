package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerIgnoreList}.
 */
class PlayerIgnoreListTest {

    @Test
    void emptyListIgnoresNoOne() {
        PlayerIgnoreList list = PlayerIgnoreList.empty();
        assertTrue(list.isEmpty());
        assertFalse(list.has("alice"));
        assertTrue(list.ignoredNames().isEmpty());
    }

    @Test
    void withAddsPlayerNormalisedToLowerCase() {
        PlayerIgnoreList list = PlayerIgnoreList.empty().with("Alice");
        assertTrue(list.has("Alice"));
        assertTrue(list.ignoredNames().contains("alice"));
        assertFalse(list.ignoredNames().contains("Alice"));
    }

    @Test
    void hasIsCaseInsensitive() {
        PlayerIgnoreList list = PlayerIgnoreList.empty().with("Bob");
        assertTrue(list.has("bob"));
        assertTrue(list.has("BOB"));
        assertTrue(list.has("BoB"));
    }

    @Test
    void withIsIdempotentForAlreadyIgnoredName() {
        PlayerIgnoreList list = PlayerIgnoreList.empty().with("Carol");
        PlayerIgnoreList again = list.with("carol");
        assertSame(list, again, "Re-adding an ignored player returns the same instance");
    }

    @Test
    void withoutRemovesPlayer() {
        PlayerIgnoreList list = PlayerIgnoreList.empty().with("Dave").with("Erin");
        PlayerIgnoreList removed = list.without("dave");
        assertFalse(removed.has("Dave"));
        assertTrue(removed.has("Erin"));
    }

    @Test
    void withoutIsNoOpWhenNotIgnored() {
        PlayerIgnoreList list = PlayerIgnoreList.empty().with("Frank");
        PlayerIgnoreList unchanged = list.without("nobody");
        assertSame(list, unchanged, "Removing an absent player returns the same instance");
    }

    @Test
    void constructorNormalisesAndDeduplicates() {
        PlayerIgnoreList list = new PlayerIgnoreList(List.of("Alice", "ALICE", "bob"));
        assertEquals(2, list.ignoredNames().size());
        assertTrue(list.has("alice"));
        assertTrue(list.has("bob"));
    }

    @Test
    void constructorIgnoresBlankAndNullTolerant() {
        PlayerIgnoreList list = new PlayerIgnoreList(java.util.Arrays.asList("Alice", "  ", null));
        assertEquals(1, list.ignoredNames().size());
        assertTrue(list.has("alice"));
    }

    @Test
    void nullConstructorArgumentYieldsEmptyList() {
        PlayerIgnoreList list = new PlayerIgnoreList(null);
        assertTrue(list.isEmpty());
    }
}
