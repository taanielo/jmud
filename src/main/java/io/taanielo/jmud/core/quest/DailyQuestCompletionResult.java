package io.taanielo.jmud.core.quest;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;

/**
 * Outcome of a {@link DailyQuestService#completeDailyQuest} attempt.
 *
 * @param success      {@code true} when the reward was granted and the active quest cleared
 * @param player       the resulting player (updated on success, unchanged on failure)
 * @param leveledUp    {@code true} when the XP reward caused a level-up
 * @param messages     messages to deliver to the player; never null
 * @param droppedItems item-reward copies that did not fit and must be dropped at the player's feet by
 *                     the caller; never null, empty when none overflowed
 */
public record DailyQuestCompletionResult(
    boolean success,
    Player player,
    boolean leveledUp,
    List<String> messages,
    List<Item> droppedItems
) {

    public DailyQuestCompletionResult {
        Objects.requireNonNull(player, "player is required");
        messages = messages == null ? List.of() : List.copyOf(messages);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
    }

    static DailyQuestCompletionResult failure(Player player, String message) {
        return new DailyQuestCompletionResult(false, player, false, List.of(message), List.of());
    }

    static DailyQuestCompletionResult success(
        Player player, boolean leveledUp, List<String> messages, List<Item> droppedItems) {
        return new DailyQuestCompletionResult(true, player, leveledUp, messages, droppedItems);
    }
}
