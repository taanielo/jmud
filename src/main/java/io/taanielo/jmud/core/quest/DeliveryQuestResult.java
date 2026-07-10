package io.taanielo.jmud.core.quest;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;

/**
 * Outcome of an NPC-delivery quest operation (granting the package or delivering it).
 *
 * <p>On success {@link #player()} holds the updated player state (package added on grant,
 * or package removed and reward applied on delivery) and {@code success} is {@code true}.
 * On failure {@link #player()} is {@code null} and {@link #messages()} explains why.
 *
 * @param player       updated player state after a successful operation; {@code null} on failure
 * @param messages     messages to deliver to the player
 * @param success      {@code true} when the operation was accepted
 * @param droppedItems item-reward copies that did not fit and must be dropped at the player's feet by
 *                     the caller; never null, empty when none overflowed
 */
public record DeliveryQuestResult(
    Player player, List<String> messages, boolean success, List<Item> droppedItems) {

    public DeliveryQuestResult {
        Objects.requireNonNull(messages, "messages is required");
        messages = List.copyOf(messages);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
    }

    /**
     * Creates a successful result carrying the updated player and one or more messages.
     *
     * @param player       the updated player; must not be null
     * @param messages     the messages to show the player
     * @param droppedItems item-reward copies that overflowed and must be dropped at the player's feet
     * @return a successful {@link DeliveryQuestResult}
     */
    public static DeliveryQuestResult success(Player player, List<String> messages, List<Item> droppedItems) {
        Objects.requireNonNull(player, "player is required");
        return new DeliveryQuestResult(player, messages, true, droppedItems);
    }

    /**
     * Creates a failed result carrying a single explanatory message.
     *
     * @param message the failure message
     * @return a failed {@link DeliveryQuestResult}
     */
    public static DeliveryQuestResult failure(String message) {
        return new DeliveryQuestResult(null, List.of(message), false, List.of());
    }
}
