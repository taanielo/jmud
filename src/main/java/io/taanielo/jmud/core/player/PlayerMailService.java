package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.tick.TickSettings;

/**
 * Domain service implementing the {@code MAIL} command's business rules: leaving an offline
 * message for another player, and listing/reading/deleting mail from one's own mailbox.
 *
 * <p>Kept separate from {@code SocketCommandContextImpl} so the capacity and validation rules
 * are unit-testable without sockets (AGENTS.md §10). Timestamps are stored as game ticks
 * (see {@link PlayerMailMessage}); this service only converts a tick delta into an
 * approximate human-readable age for display, using the configured tick interval
 * ({@link TickSettings#intervalMillis()}) rather than wall-clock time.
 */
public class PlayerMailService {

    /**
     * Leaves a message for the given recipient.
     *
     * @param recipient  the player to receive the mail; must not be null
     * @param senderName the display name of the sender
     * @param currentTick the game tick at which the message is being sent
     * @param body       the message text; must not be blank
     * @return the result; on success {@link MailResult#updatedPlayer()} is the recipient with
     *         the new message appended, on failure (blank body or full mailbox) no player
     *         is returned
     */
    public MailResult send(Player recipient, String senderName, long currentTick, String body) {
        Objects.requireNonNull(recipient, "recipient is required");
        Objects.requireNonNull(senderName, "senderName is required");
        String trimmedBody = body == null ? "" : body.trim();
        if (trimmedBody.isEmpty()) {
            return MailResult.failure("Mail what message?");
        }
        if (recipient.mailbox().isFull()) {
            return MailResult.failure(recipient.getUsername().getValue() + "'s mailbox is full.");
        }
        Player updated = recipient.withMailbox(
            recipient.mailbox().add(new PlayerMailMessage(senderName, currentTick, trimmedBody, false)));
        return MailResult.success("You leave a message for " + recipient.getUsername().getValue() + ".", updated);
    }

    /**
     * Lists the given player's mail, oldest first, with sender, relative age, and a preview
     * of each message. Viewing the list marks every message read, clearing the unread count
     * shown at login.
     *
     * @param player      the player whose mailbox to list
     * @param currentTick the current game tick, used to compute each message's relative age
     * @return a listing result; failure when the mailbox is empty
     */
    public MailResult list(Player player, long currentTick) {
        Objects.requireNonNull(player, "player is required");
        List<PlayerMailMessage> messages = player.mailbox().messages();
        if (messages.isEmpty()) {
            return MailResult.failure("You have no mail.");
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            PlayerMailMessage message = messages.get(i);
            String preview = preview(message.body());
            lines.add("  " + (i + 1) + ". " + message.sender() + " (" + relativeAge(currentTick, message.sentAtTick())
                + ") - " + preview);
        }
        Player updated = player.withMailbox(player.mailbox().markAllRead());
        return MailResult.listing(lines, updated);
    }

    /**
     * Shows the full text of the message at the given one-based index, and marks it read.
     *
     * @param player the player whose mailbox to read from
     * @param index  one-based index as shown by {@link #list}
     * @return the result; failure when the index is out of range
     */
    public MailResult read(Player player, int index) {
        Objects.requireNonNull(player, "player is required");
        List<PlayerMailMessage> messages = player.mailbox().messages();
        int zeroBased = index - 1;
        if (zeroBased < 0 || zeroBased >= messages.size()) {
            return MailResult.failure("No mail numbered " + index + ".");
        }
        PlayerMailMessage message = messages.get(zeroBased);
        Player updated = player.withMailbox(player.mailbox().markRead(zeroBased));
        List<String> lines = List.of("From " + message.sender() + ":", message.body());
        return MailResult.listing(lines, updated);
    }

    /**
     * Removes the message at the given one-based index from the player's mailbox.
     *
     * @param player the player whose mailbox to delete from
     * @param index  one-based index as shown by {@link #list}
     * @return the result; failure when the index is out of range
     */
    public MailResult delete(Player player, int index) {
        Objects.requireNonNull(player, "player is required");
        List<PlayerMailMessage> messages = player.mailbox().messages();
        int zeroBased = index - 1;
        if (zeroBased < 0 || zeroBased >= messages.size()) {
            return MailResult.failure("No mail numbered " + index + ".");
        }
        Player updated = player.withMailbox(player.mailbox().remove(zeroBased));
        return MailResult.success("Message " + index + " deleted.", updated);
    }

    private static String preview(String body) {
        String singleLine = body.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 40 ? singleLine.substring(0, 40) + "..." : singleLine;
    }

    private static String relativeAge(long currentTick, long sentAtTick) {
        long deltaTicks = Math.max(0, currentTick - sentAtTick);
        long deltaSeconds = deltaTicks * TickSettings.intervalMillis() / 1000;
        if (deltaSeconds < 60) {
            return "just now";
        }
        long minutes = deltaSeconds / 60;
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }
}
