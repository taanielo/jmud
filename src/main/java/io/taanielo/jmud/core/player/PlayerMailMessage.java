package io.taanielo.jmud.core.player;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single message held in a player's {@link PlayerMailbox}.
 *
 * <p>{@code sentAtTick} is the game tick (per AGENTS.md §5's determinism rule; see
 * {@link io.taanielo.jmud.core.tick.TickClock}) at which the message was left, not a wall-clock
 * timestamp, so replaying the same sequence of ticks always produces the same stored state.
 * Human-readable relative times ("2 hours ago") are derived from this value only at display
 * time and never feed back into game state.
 *
 * <p>{@code attachedGold} is an optional gold attachment carried by the message (see
 * {@code MAIL GOLD}). It defaults to {@code 0}, so player saves written before gold attachments
 * existed (whose JSON lacks the field) load with no attached gold. The gold is credited to the
 * recipient exactly once — when the message is read or deleted — after which the attachment is
 * cleared via {@link #withoutAttachment()} so it cannot be claimed twice.
 */
public record PlayerMailMessage(String sender, long sentAtTick, String body, boolean read, int attachedGold) {

    @JsonCreator
    public PlayerMailMessage(
        @JsonProperty("sender") String sender,
        @JsonProperty("sentAtTick") long sentAtTick,
        @JsonProperty("body") String body,
        @JsonProperty("read") boolean read,
        @JsonProperty("attachedGold") int attachedGold
    ) {
        this.sender = Objects.requireNonNull(sender, "sender is required");
        this.sentAtTick = sentAtTick;
        this.body = Objects.requireNonNull(body, "body is required");
        this.read = read;
        this.attachedGold = Math.max(0, attachedGold);
    }

    /**
     * Creates a message with no gold attachment.
     */
    public PlayerMailMessage(String sender, long sentAtTick, String body, boolean read) {
        this(sender, sentAtTick, body, read, 0);
    }

    /**
     * Returns {@code true} when this message carries an unclaimed gold attachment.
     */
    @JsonIgnore
    public boolean hasAttachment() {
        return attachedGold > 0;
    }

    /**
     * Returns a copy of this message with {@link #read()} set to {@code true}.
     */
    public PlayerMailMessage markRead() {
        return read ? this : new PlayerMailMessage(sender, sentAtTick, body, true, attachedGold);
    }

    /**
     * Returns a copy of this message with any gold attachment cleared, so the gold cannot be
     * claimed a second time.
     */
    public PlayerMailMessage withoutAttachment() {
        return attachedGold == 0 ? this : new PlayerMailMessage(sender, sentAtTick, body, read, 0);
    }
}
