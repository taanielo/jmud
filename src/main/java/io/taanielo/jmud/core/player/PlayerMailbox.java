package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Holds the offline messages ({@code MAIL} command) waiting for a player.
 *
 * <p>Messages are kept in the order they were received (oldest first). The mailbox is
 * capped at {@value #CAPACITY} entries to bound save-file growth; once full, new mail is
 * rejected rather than silently dropping older messages (see {@link PlayerMailService#send}).
 */
public class PlayerMailbox {

    /** Maximum number of messages a mailbox may hold at once. */
    public static final int CAPACITY = 20;

    private final List<PlayerMailMessage> messages;

    public PlayerMailbox(List<PlayerMailMessage> messages) {
        this.messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
    }

    /**
     * Returns an empty {@link PlayerMailbox} instance.
     */
    public static PlayerMailbox empty() {
        return new PlayerMailbox(List.of());
    }

    /**
     * Returns the messages currently held, oldest first.
     */
    public List<PlayerMailMessage> messages() {
        return messages;
    }

    /**
     * Returns {@code true} when the mailbox has no unread messages.
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * Returns {@code true} when the mailbox is at capacity and cannot accept new mail.
     */
    public boolean isFull() {
        return messages.size() >= CAPACITY;
    }

    /**
     * Returns the number of messages that have not yet been marked read.
     */
    public long unreadCount() {
        return messages.stream().filter(m -> !m.read()).count();
    }

    /**
     * Returns a copy of this mailbox with the given message appended.
     *
     * @param message the message to append; must not be null
     * @throws IllegalStateException if the mailbox is already full
     */
    public PlayerMailbox add(PlayerMailMessage message) {
        Objects.requireNonNull(message, "message is required");
        if (isFull()) {
            throw new IllegalStateException("Mailbox is full");
        }
        List<PlayerMailMessage> next = new ArrayList<>(messages);
        next.add(message);
        return new PlayerMailbox(next);
    }

    /**
     * Returns a copy of this mailbox with the message at the given zero-based index removed.
     *
     * @param index zero-based index into {@link #messages()}
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public PlayerMailbox remove(int index) {
        if (index < 0 || index >= messages.size()) {
            throw new IndexOutOfBoundsException("No mail at index " + index);
        }
        List<PlayerMailMessage> next = new ArrayList<>(messages);
        next.remove(index);
        return new PlayerMailbox(next);
    }

    /**
     * Returns a copy of this mailbox with the message at the given zero-based index marked read.
     *
     * @param index zero-based index into {@link #messages()}
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public PlayerMailbox markRead(int index) {
        if (index < 0 || index >= messages.size()) {
            throw new IndexOutOfBoundsException("No mail at index " + index);
        }
        List<PlayerMailMessage> next = new ArrayList<>(messages);
        next.set(index, next.get(index).markRead());
        return new PlayerMailbox(next);
    }

    /**
     * Returns a copy of this mailbox with every message marked read.
     */
    public PlayerMailbox markAllRead() {
        if (unreadCount() == 0) {
            return this;
        }
        List<PlayerMailMessage> next = messages.stream().map(PlayerMailMessage::markRead).toList();
        return new PlayerMailbox(next);
    }
}
