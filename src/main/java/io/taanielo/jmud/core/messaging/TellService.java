package io.taanielo.jmud.core.messaging;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.taanielo.jmud.core.authentication.Username;

/**
 * In-memory tracker of the most recent private-message sender per recipient.
 *
 * <p>Backs the {@code REPLY} / {@code R} command: whenever a player <em>receives</em> a
 * {@code TELL} or {@code WHISPER}, the sender is recorded here keyed by the recipient, so the
 * recipient can answer without retyping the sender's name.
 *
 * <p>State is purely ephemeral, mirroring {@code PartyService.pendingInvites}: it is never
 * persisted to a player's save file and does not survive a server restart. The single map is a
 * {@link ConcurrentHashMap} because it is written from connection reader threads while being read
 * during command execution; each individual put/get is atomic and no compound invariant spans
 * multiple entries.
 */
public class TellService {

    /** recipient username → username of the last player who told/whispered them. */
    private final Map<Username, Username> lastSenderByRecipient = new ConcurrentHashMap<>();

    /**
     * Records that {@code recipient} just received a private message from {@code sender}, making
     * {@code sender} the target of the recipient's next {@code REPLY}.
     *
     * @param recipient the player who received the message
     * @param sender    the player who sent it
     */
    public void recordReceivedTell(Username recipient, Username sender) {
        Objects.requireNonNull(recipient, "recipient is required");
        Objects.requireNonNull(sender, "sender is required");
        lastSenderByRecipient.put(recipient, sender);
    }

    /**
     * Returns the player who most recently sent {@code recipient} a private message this session,
     * if any.
     *
     * @param recipient the player who wants to reply
     * @return the last sender, or empty when no tell/whisper has been received this session
     */
    public Optional<Username> lastSender(Username recipient) {
        Objects.requireNonNull(recipient, "recipient is required");
        return Optional.ofNullable(lastSenderByRecipient.get(recipient));
    }
}
