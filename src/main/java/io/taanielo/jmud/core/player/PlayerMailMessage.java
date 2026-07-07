package io.taanielo.jmud.core.player;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single message held in a player's {@link PlayerMailbox}.
 *
 * <p>{@code sentAtTick} is the game tick (per AGENTS.md §5's determinism rule; see
 * {@link io.taanielo.jmud.core.tick.TickClock}) at which the message was left, not a wall-clock
 * timestamp, so replaying the same sequence of ticks always produces the same stored state.
 * Human-readable relative times ("2 hours ago") are derived from this value only at display
 * time and never feed back into game state.
 */
public record PlayerMailMessage(String sender, long sentAtTick, String body, boolean read) {

    @JsonCreator
    public PlayerMailMessage(
        @JsonProperty("sender") String sender,
        @JsonProperty("sentAtTick") long sentAtTick,
        @JsonProperty("body") String body,
        @JsonProperty("read") boolean read
    ) {
        this.sender = Objects.requireNonNull(sender, "sender is required");
        this.sentAtTick = sentAtTick;
        this.body = Objects.requireNonNull(body, "body is required");
        this.read = read;
    }

    /**
     * Returns a copy of this message with {@link #read()} set to {@code true}.
     */
    public PlayerMailMessage markRead() {
        return read ? this : new PlayerMailMessage(sender, sentAtTick, body, true);
    }
}
