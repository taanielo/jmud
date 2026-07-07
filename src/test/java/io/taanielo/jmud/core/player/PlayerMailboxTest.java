package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerMailbox}.
 */
class PlayerMailboxTest {

    @Test
    void emptyHasNoMessages() {
        PlayerMailbox mailbox = PlayerMailbox.empty();
        assertTrue(mailbox.isEmpty());
        assertFalse(mailbox.isFull());
        assertEquals(0, mailbox.unreadCount());
    }

    @Test
    void addAppendsMessage() {
        PlayerMailbox mailbox = PlayerMailbox.empty().add(new PlayerMailMessage("alice", 1, "hi", false));
        assertEquals(1, mailbox.messages().size());
        assertEquals(1, mailbox.unreadCount());
    }

    @Test
    void addRejectsWhenFull() {
        List<PlayerMailMessage> full = new ArrayList<>();
        for (int i = 0; i < PlayerMailbox.CAPACITY; i++) {
            full.add(new PlayerMailMessage("alice", i, "msg " + i, false));
        }
        PlayerMailbox mailbox = new PlayerMailbox(full);
        assertTrue(mailbox.isFull());
        assertThrows(IllegalStateException.class,
            () -> mailbox.add(new PlayerMailMessage("bob", 100, "overflow", false)));
    }

    @Test
    void removeDeletesMessageAtIndex() {
        PlayerMailbox mailbox = PlayerMailbox.empty()
            .add(new PlayerMailMessage("alice", 1, "first", false))
            .add(new PlayerMailMessage("bob", 2, "second", false));
        PlayerMailbox updated = mailbox.remove(0);
        assertEquals(1, updated.messages().size());
        assertEquals("bob", updated.messages().get(0).sender());
    }

    @Test
    void removeThrowsForInvalidIndex() {
        PlayerMailbox mailbox = PlayerMailbox.empty();
        assertThrows(IndexOutOfBoundsException.class, () -> mailbox.remove(0));
    }

    @Test
    void markReadUpdatesSingleMessage() {
        PlayerMailbox mailbox = PlayerMailbox.empty()
            .add(new PlayerMailMessage("alice", 1, "first", false))
            .add(new PlayerMailMessage("bob", 2, "second", false));
        PlayerMailbox updated = mailbox.markRead(0);
        assertTrue(updated.messages().get(0).read());
        assertFalse(updated.messages().get(1).read());
        assertEquals(1, updated.unreadCount());
    }

    @Test
    void markAllReadClearsUnreadCount() {
        PlayerMailbox mailbox = PlayerMailbox.empty()
            .add(new PlayerMailMessage("alice", 1, "first", false))
            .add(new PlayerMailMessage("bob", 2, "second", false));
        PlayerMailbox updated = mailbox.markAllRead();
        assertEquals(0, updated.unreadCount());
    }
}
