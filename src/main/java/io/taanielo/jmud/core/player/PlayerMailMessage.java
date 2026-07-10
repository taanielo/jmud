package io.taanielo.jmud.core.player;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.dto.ItemDto;
import io.taanielo.jmud.core.world.dto.ItemMapper;

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
 *
 * <p>{@code attachedItem} is an optional item attachment carried by the message (see
 * {@code MAIL ITEM}). Unlike gold, the domain {@link Item} type has no Jackson creator and cannot
 * be re-hydrated directly, so the attachment is persisted through its serializable
 * {@link ItemDto} form (mapped by {@link ItemMapper}) under the JSON property {@code attachedItem}.
 * It defaults to {@code null}, so player saves written before item attachments existed (whose JSON
 * lacks the field) load with no attached item, mirroring the {@code attachedGold} backward
 * compatibility. The item is credited to the recipient exactly once — when the message is read or
 * deleted — after which the attachment is cleared via {@link #withoutItemAttachment()} so it
 * cannot be claimed twice. The domain {@link Item} is exposed via {@link #resolveAttachedItem()}.
 */
public record PlayerMailMessage(
    String sender,
    long sentAtTick,
    String body,
    boolean read,
    int attachedGold,
    @JsonProperty("attachedItem") @Nullable ItemDto attachedItem
) {

    private static final ItemMapper ITEM_MAPPER = new ItemMapper();

    @JsonCreator
    public PlayerMailMessage(
        @JsonProperty("sender") String sender,
        @JsonProperty("sentAtTick") long sentAtTick,
        @JsonProperty("body") String body,
        @JsonProperty("read") boolean read,
        @JsonProperty("attachedGold") int attachedGold,
        @JsonProperty("attachedItem") @Nullable ItemDto attachedItem
    ) {
        this.sender = Objects.requireNonNull(sender, "sender is required");
        this.sentAtTick = sentAtTick;
        this.body = Objects.requireNonNull(body, "body is required");
        this.read = read;
        this.attachedGold = Math.max(0, attachedGold);
        this.attachedItem = attachedItem;
    }

    /**
     * Creates a message with no attachments.
     */
    public PlayerMailMessage(String sender, long sentAtTick, String body, boolean read) {
        this(sender, sentAtTick, body, read, 0, null);
    }

    /**
     * Creates a message with a gold attachment and no item attachment.
     */
    public PlayerMailMessage(String sender, long sentAtTick, String body, boolean read, int attachedGold) {
        this(sender, sentAtTick, body, read, attachedGold, null);
    }

    /**
     * Creates a message carrying an optional gold and/or item attachment, converting the domain
     * {@link Item} into its persistable {@link ItemDto} form.
     *
     * @param sender       the display name of the sender
     * @param sentAtTick   the game tick at which the message was left
     * @param body         the message text
     * @param read         whether the message has been read
     * @param attachedGold the gold attached (0 for none)
     * @param attachedItem the item attached, or {@code null} for none
     * @return a new message carrying the given attachments
     */
    public static PlayerMailMessage withAttachments(
        String sender, long sentAtTick, String body, boolean read, int attachedGold, @Nullable Item attachedItem) {
        return new PlayerMailMessage(
            sender, sentAtTick, body, read, attachedGold,
            attachedItem == null ? null : ITEM_MAPPER.toDto(attachedItem));
    }

    /**
     * Returns {@code true} when this message carries an unclaimed gold attachment.
     */
    @JsonIgnore
    public boolean hasAttachment() {
        return attachedGold > 0;
    }

    /**
     * Returns {@code true} when this message carries an unclaimed item attachment.
     */
    @JsonIgnore
    public boolean hasItemAttachment() {
        return attachedItem != null;
    }

    /**
     * Returns the domain {@link Item} attached to this message, or {@code null} when none is
     * attached. The item is re-hydrated on demand from its persisted {@link ItemDto} form.
     */
    @JsonIgnore
    public @Nullable Item resolveAttachedItem() {
        return attachedItem == null ? null : ITEM_MAPPER.toDomain(attachedItem);
    }

    /**
     * Returns a copy of this message with {@link #read()} set to {@code true}.
     */
    public PlayerMailMessage markRead() {
        return read ? this : new PlayerMailMessage(sender, sentAtTick, body, true, attachedGold, attachedItem);
    }

    /**
     * Returns a copy of this message with any gold attachment cleared, so the gold cannot be
     * claimed a second time.
     */
    public PlayerMailMessage withoutAttachment() {
        return attachedGold == 0 ? this : new PlayerMailMessage(sender, sentAtTick, body, read, 0, attachedItem);
    }

    /**
     * Returns a copy of this message with any item attachment cleared, so the item cannot be
     * claimed a second time.
     */
    public PlayerMailMessage withoutItemAttachment() {
        return attachedItem == null ? this : new PlayerMailMessage(sender, sentAtTick, body, read, attachedGold, null);
    }
}
