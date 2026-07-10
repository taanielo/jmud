package io.taanielo.jmud.core.quest;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;

/**
 * Outcome of recording a room visit against a player's active exploration quest.
 *
 * <p>Only produced when a visit actually advanced the quest: either a required room was newly
 * discovered ({@code completed} is {@code false}) or the final required room was entered and the
 * reward applied ({@code completed} is {@code true}). {@link #player()} always holds the updated
 * player snapshot; {@link #leveledUp()} is {@code true} only when the completion XP reward caused
 * a level-up.
 *
 * @param player       updated player state after the visit; never {@code null}
 * @param completed    {@code true} when the visit completed the quest and applied the reward
 * @param leveledUp    {@code true} when the completion XP reward caused a level-up
 * @param messages     messages to deliver to the player
 * @param droppedItems item-reward copies that did not fit and must be dropped at the player's feet by
 *                     the caller; never null, empty when none overflowed
 */
public record ExplorationQuestResult(
    Player player, boolean completed, boolean leveledUp, List<String> messages, List<Item> droppedItems) {

    public ExplorationQuestResult {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(messages, "messages is required");
        messages = List.copyOf(messages);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
    }

    /**
     * Creates a progress result for a newly-visited required room that did not complete the quest.
     *
     * @param player   the updated player carrying the advanced quest progress
     * @param messages the progress messages to show the player
     * @return a non-completing {@link ExplorationQuestResult}
     */
    public static ExplorationQuestResult progress(Player player, List<String> messages) {
        return new ExplorationQuestResult(player, false, false, messages, List.of());
    }

    /**
     * Creates a completion result carrying the rewarded player.
     *
     * @param player       the updated player with the reward applied and the quest cleared
     * @param leveledUp    whether the XP reward caused a level-up
     * @param messages     the completion messages to show the player
     * @param droppedItems item-reward copies that overflowed and must be dropped at the player's feet
     * @return a completing {@link ExplorationQuestResult}
     */
    public static ExplorationQuestResult completed(
        Player player, boolean leveledUp, List<String> messages, List<Item> droppedItems) {
        return new ExplorationQuestResult(player, true, leveledUp, messages, droppedItems);
    }
}
