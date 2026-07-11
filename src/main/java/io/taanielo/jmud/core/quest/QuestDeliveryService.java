package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.achievement.Achievement;
import io.taanielo.jmud.core.achievement.AchievementService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.quest.QuestItemRewardService.ItemRewardGrant;
import io.taanielo.jmud.core.quest.QuestReputationRewardService.ReputationRewardGrant;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Domain service for delivery-type quests where a player must collect a specific
 * drop item and turn in the required number to claim a reward.
 *
 * <p>Progress is determined dynamically by counting qualifying items in the
 * player's inventory rather than maintaining a separate counter, ensuring
 * the count is always in sync with actual held items.
 *
 * <p>This service is stateless and thread-safe.
 */
@Slf4j
public class QuestDeliveryService {

    private final QuestRepository questRepository;
    private final QuestItemRewardService itemRewardService;
    private final QuestReputationRewardService reputationRewardService;
    /** Optional achievement service that unlocks quest-milestone achievements on completion; may be null. */
    private AchievementService achievementService;

    public QuestDeliveryService(QuestRepository questRepository) {
        this(questRepository, null, null);
    }

    /**
     * Creates a delivery service that additionally grants configured item rewards on completion.
     *
     * @param questRepository   the quest repository; must not be null
     * @param itemRewardService grants a quest's optional item reward, or {@code null} to disable item
     *                          rewards
     */
    public QuestDeliveryService(QuestRepository questRepository, QuestItemRewardService itemRewardService) {
        this(questRepository, itemRewardService, null);
    }

    /**
     * Creates a delivery service that additionally grants configured item and reputation rewards on
     * completion.
     *
     * @param questRepository         the quest repository; must not be null
     * @param itemRewardService       grants a quest's optional item reward, or {@code null} to disable
     *                                item rewards
     * @param reputationRewardService applies a quest's optional reputation reward, or {@code null} to
     *                                disable reputation rewards
     */
    public QuestDeliveryService(QuestRepository questRepository, QuestItemRewardService itemRewardService,
            QuestReputationRewardService reputationRewardService) {
        this.questRepository = Objects.requireNonNull(questRepository, "questRepository is required");
        this.itemRewardService = itemRewardService;
        this.reputationRewardService = reputationRewardService;
    }

    /**
     * Registers the achievement service that unlocks quest-milestone achievements (e.g. "Errand
     * Runner" at 5 completed quests) when a one-time delivery quest is completed via this service.
     *
     * @param achievementService the achievement service; may be null to disable achievement unlocks
     */
    public void setAchievementService(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    /**
     * Checks whether picking up an item should trigger a delivery quest progress message.
     *
     * <p>Returns a progress string (e.g. {@code "Rat Tail: 3/5 collected."}) when the
     * player has an active delivery quest whose {@code dropItemId} matches the given
     * item id. Returns empty when there is no delivery quest or the item does not match.
     *
     * @param player  the player who just picked up an item (inventory already updated); must not be null
     * @param itemId  the id of the item that was picked up; must not be null
     * @return an optional progress message
     */
    public Optional<String> checkPickupProgress(Player player, ItemId itemId) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(itemId, "itemId is required");

        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            return Optional.empty();
        }

        QuestTemplate template;
        try {
            template = questRepository.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest template {}: {}", active.templateId(), e.getMessage());
            return Optional.empty();
        }

        if (template == null || !template.isDeliveryQuest()) {
            return Optional.empty();
        }

        if (!itemId.getValue().equalsIgnoreCase(template.dropItemId())) {
            return Optional.empty();
        }

        int held = countMatchingItems(player, template.dropItemId());
        return Optional.of(template.name() + ": " + held + "/" + template.requiredDropCount() + " collected.");
    }

    /**
     * Attempts to deliver collected items to the NPC for a reward.
     *
     * <p>Checks that the player holds at least {@code requiredDropCount} of the qualifying
     * item. If so, removes exactly that many items from inventory, grants gold and XP,
     * and clears the active quest. If not, returns a failure message explaining how many
     * are still needed.
     *
     * <p>Callers are responsible for the room/location check before invoking this method.
     *
     * @param player the player attempting to deliver; must not be null
     * @return a {@link DeliverResult} with the outcome
     */
    public DeliverResult deliver(Player player) {
        Objects.requireNonNull(player, "player is required");

        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            return DeliverResult.failure("You have no active delivery contract.");
        }

        QuestTemplate template;
        try {
            template = questRepository.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest template on deliver {}: {}", active.templateId(), e.getMessage());
            return DeliverResult.failure("Quest data unavailable. Try again.");
        }

        if (template == null) {
            return DeliverResult.failure("Unknown quest template. Use QUEST ABANDON to clear it.");
        }

        if (!template.isDeliveryQuest()) {
            return DeliverResult.failure(
                "That contract requires kills, not item delivery. Use QUEST COMPLETE instead.");
        }

        int held = countMatchingItems(player, template.dropItemId());
        if (held < template.requiredDropCount()) {
            int stillNeeded = template.requiredDropCount() - held;
            return DeliverResult.failure(
                "You only have " + held + "/" + template.requiredDropCount()
                + " " + template.name().toLowerCase() + " items. "
                + "You still need " + stillNeeded + " more.");
        }

        // Remove exactly requiredDropCount items from inventory
        Player updated = removeItems(player, template.dropItemId(), template.requiredDropCount());
        updated = updated.withActiveQuest(null).addGold(template.goldReward());

        // Add XP directly to the player's cumulative experience total
        long newXp = updated.getExperience() + template.xpReward();
        updated = updated.withIdentity(updated.identity().withExperience(newXp));

        ItemRewardGrant itemGrant = itemRewardService != null
            ? itemRewardService.grant(updated, template)
            : ItemRewardGrant.none(updated);
        updated = itemGrant.player();

        List<String> messages = new ArrayList<>();
        messages.add("The Guild Clerk nods approvingly. Contract complete: " + template.name() + ".");
        messages.add(QuestItemRewardService.receiveLine(
            template.goldReward(), template.xpReward(), itemGrant.description()));
        messages.addAll(itemGrant.messages());

        ReputationRewardGrant reputationGrant = reputationRewardService != null
            ? reputationRewardService.grant(updated, template)
            : ReputationRewardGrant.none(updated);
        updated = reputationGrant.player();
        reputationGrant.messageText().ifPresent(messages::add);

        String titleReward = template.titleReward();
        if (titleReward != null && !updated.titles().has(titleReward)) {
            updated = updated.grantTitle(titleReward);
            messages.add("You have earned the title: " + titleReward + "!");
        }

        if (!template.isRepeatable()) {
            updated = updated.withCompletedQuest(template.id());
            updated = unlockQuestAchievements(updated, messages);
        }

        return DeliverResult.success(updated, messages, itemGrant.droppedItems());
    }

    // ── private helpers ────────────────────────────────────────────────

    /**
     * Checks and unlocks any milestone achievements the just-completed one-time delivery quest
     * satisfied, appending an {@code "Achievement unlocked: <name>!"} line to {@code messages} per new
     * unlock. Returns the player unchanged when no achievement service is configured or nothing new
     * unlocked.
     *
     * @param player   the player who has just had a completed quest recorded; must not be null
     * @param messages the mutable message list to append unlock notices to; must not be null
     * @return the player with any newly unlocked achievements applied
     */
    private Player unlockQuestAchievements(Player player, List<String> messages) {
        if (achievementService == null) {
            return player;
        }
        AchievementService.UnlockResult result = achievementService.checkAndUnlock(player);
        for (Achievement unlocked : result.newlyUnlocked()) {
            messages.add("Achievement unlocked: " + unlocked.name() + "!");
        }
        return result.player();
    }

    private int countMatchingItems(Player player, String dropItemId) {
        int count = 0;
        for (Item item : player.getInventory()) {
            if (item.getId().getValue().equalsIgnoreCase(dropItemId)) {
                count++;
            }
        }
        return count;
    }

    private Player removeItems(Player player, String dropItemId, int count) {
        List<Item> inventory = player.getInventory();
        List<Item> remaining = new ArrayList<>(inventory.size());
        int stillToRemove = count;
        for (Item item : inventory) {
            if (stillToRemove > 0 && item.getId().getValue().equalsIgnoreCase(dropItemId)) {
                stillToRemove--;
                // skip — this item is turned in
            } else {
                remaining.add(item);
            }
        }
        return player.withInventory(remaining);
    }

    /**
     * Result of a delivery attempt.
     *
     * @param player       updated player state after a successful delivery; {@code null} on failure
     * @param messages     messages to deliver to the player
     * @param success      {@code true} when the delivery was accepted and reward granted
     * @param droppedItems item-reward copies that did not fit and must be dropped at the player's feet
     *                     by the caller; never null, empty when none overflowed
     */
    public record DeliverResult(
        Player player, List<String> messages, boolean success, List<Item> droppedItems) {

        public DeliverResult {
            droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
        }

        static DeliverResult success(Player player, List<String> messages, List<Item> droppedItems) {
            return new DeliverResult(player, List.copyOf(messages), true, droppedItems);
        }

        static DeliverResult failure(String message) {
            return new DeliverResult(null, List.of(message), false, List.of());
        }
    }
}
