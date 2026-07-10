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
     * Leaves a message for the given recipient with a gold attachment. The gold is deducted from
     * the sender's on-hand gold immediately and credited to the recipient only when they read or
     * delete the message.
     *
     * @param sender      the player sending the gold; their on-hand gold is deducted on success
     * @param recipient   the player to receive the mail and gold; must not be null
     * @param currentTick the game tick at which the message is being sent
     * @param body        the message text; must not be blank
     * @param amount      the gold to attach; must be a positive amount the sender can afford
     * @return the result; on success {@link MailResult#updatedPlayer()} is the recipient with the
     *         new message and {@link MailResult#updatedSender()} is the sender with gold deducted.
     *         On failure (blank body, non-positive amount, insufficient gold, or full mailbox)
     *         no player is returned and no gold moves.
     */
    public MailResult sendGold(Player sender, Player recipient, long currentTick, String body, int amount) {
        Objects.requireNonNull(sender, "sender is required");
        Objects.requireNonNull(recipient, "recipient is required");
        String trimmedBody = body == null ? "" : body.trim();
        if (trimmedBody.isEmpty()) {
            return MailResult.failure("Mail what message?");
        }
        if (amount <= 0) {
            return MailResult.failure("How much gold do you want to send?");
        }
        if (sender.getGold() < amount) {
            return MailResult.failure("You don't have that much gold.");
        }
        if (recipient.mailbox().isFull()) {
            return MailResult.failure(recipient.getUsername().getValue() + "'s mailbox is full.");
        }
        String senderName = sender.getUsername().getValue();
        Player updatedRecipient = recipient.withMailbox(
            recipient.mailbox().add(new PlayerMailMessage(senderName, currentTick, trimmedBody, false, amount)));
        Player updatedSender = sender.addGold(-amount);
        return MailResult.sentWithGold(
            "You leave a message for " + recipient.getUsername().getValue() + " with " + amount + " gold attached.",
            updatedRecipient,
            updatedSender);
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
            String goldMarker = message.hasAttachment() ? " [" + message.attachedGold() + " gold]" : "";
            lines.add("  " + (i + 1) + ". " + message.sender() + " (" + relativeAge(currentTick, message.sentAtTick())
                + ")" + goldMarker + " - " + preview);
        }
        Player updated = player.withMailbox(player.mailbox().markAllRead());
        return MailResult.listing(lines, updated);
    }

    /**
     * Shows the full text of the message at the given one-based index, and marks it read. If the
     * message carries a gold attachment, the gold is credited to the reader and the attachment is
     * cleared so it cannot be claimed again on a subsequent read.
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
        PlayerMailbox mailbox = player.mailbox().markRead(zeroBased);
        Player updated = player;
        List<String> lines = new ArrayList<>();
        lines.add("From " + message.sender() + ":");
        lines.add(message.body());
        if (message.hasAttachment()) {
            updated = updated.addGold(message.attachedGold());
            mailbox = mailbox.clearAttachment(zeroBased);
            lines.add("You collect " + message.attachedGold() + " gold attached to this message.");
        }
        updated = updated.withMailbox(mailbox);
        return MailResult.listing(lines, updated);
    }

    /**
     * Removes the message at the given one-based index from the player's mailbox. If the message
     * still carries an unclaimed gold attachment, the gold is credited to the player first so it
     * is never silently lost.
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
        PlayerMailMessage message = messages.get(zeroBased);
        Player updated = player;
        String creditNotice = "";
        if (message.hasAttachment()) {
            updated = updated.addGold(message.attachedGold());
            creditNotice = " " + message.attachedGold() + " gold was credited to you.";
        }
        updated = updated.withMailbox(updated.mailbox().remove(zeroBased));
        return MailResult.success("Message " + index + " deleted." + creditNotice, updated);
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
