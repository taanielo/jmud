package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerFriendList}.
 */
class PlayerFriendListTest {

    @Test
    void emptyListHasNoFriends() {
        PlayerFriendList list = PlayerFriendList.empty();
        assertTrue(list.isEmpty());
        assertFalse(list.has("alice"));
        assertTrue(list.friendNames().isEmpty());
    }

    @Test
    void withAddsPlayerNormalisedToLowerCase() {
        PlayerFriendList list = PlayerFriendList.empty().with("Alice");
        assertTrue(list.has("Alice"));
        assertTrue(list.friendNames().contains("alice"));
        assertFalse(list.friendNames().contains("Alice"));
    }

    @Test
    void hasIsCaseInsensitive() {
        PlayerFriendList list = PlayerFriendList.empty().with("Bob");
        assertTrue(list.has("bob"));
        assertTrue(list.has("BOB"));
        assertTrue(list.has("BoB"));
    }

    @Test
    void withIsIdempotentForAlreadyFriendedName() {
        PlayerFriendList list = PlayerFriendList.empty().with("Carol");
        PlayerFriendList again = list.with("carol");
        assertSame(list, again, "Re-adding a friend returns the same instance");
    }

    @Test
    void withoutRemovesPlayer() {
        PlayerFriendList list = PlayerFriendList.empty().with("Dave").with("Erin");
        PlayerFriendList removed = list.without("dave");
        assertFalse(removed.has("Dave"));
        assertTrue(removed.has("Erin"));
    }

    @Test
    void withoutIsNoOpWhenNotFriend() {
        PlayerFriendList list = PlayerFriendList.empty().with("Frank");
        PlayerFriendList unchanged = list.without("nobody");
        assertSame(list, unchanged, "Removing an absent player returns the same instance");
    }

    @Test
    void constructorNormalisesAndDeduplicates() {
        PlayerFriendList list = new PlayerFriendList(List.of("Alice", "ALICE", "bob"));
        assertEquals(2, list.friendNames().size());
        assertTrue(list.has("alice"));
        assertTrue(list.has("bob"));
    }

    @Test
    void constructorIgnoresBlankAndNullTolerant() {
        PlayerFriendList list = new PlayerFriendList(java.util.Arrays.asList("Alice", "  ", null));
        assertEquals(1, list.friendNames().size());
        assertTrue(list.has("alice"));
    }

    @Test
    void nullConstructorArgumentYieldsEmptyList() {
        PlayerFriendList list = new PlayerFriendList(null);
        assertTrue(list.isEmpty());
    }
}
