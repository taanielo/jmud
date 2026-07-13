package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.achievement.Achievement;
import io.taanielo.jmud.core.achievement.AchievementService;
import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.quest.QuestItemRewardService.ItemRewardGrant;
import io.taanielo.jmud.core.quest.QuestReputationRewardService.ReputationRewardGrant;
import io.taanielo.jmud.core.world.Item;

/**
 * Domain service that processes mob kills against a player's active quest.
 *
 * <p>When a mob matching the quest's target is killed, the kill counter is
 * decremented and a progress message is produced. When the counter reaches
 * zero the player is notified to return to the Guild Clerk.
 *
 * <p>This service is stateless and thread-safe.
 */
@Slf4j
public class QuestKillService {

    private final QuestRepository questRepository;
    private final QuestItemRewardService itemRewardService;
    private final QuestReputationRewardService reputationRewardService;
    /** Optional achievement service that unlocks quest-milestone achievements on completion; may be null. */
    private AchievementService achievementService;
    /** Awards quest XP and applies class-differentiated level-up gains; defaults to legacy flat gains. */
    private LevelUpService levelUpService = new LevelUpService();

    public QuestKillService(QuestRepository questRepository) {
        this(questRepository, null, null);
    }

    /**
     * Creates a kill-quest service that additionally grants configured item rewards on completion.
     *
     * @param questRepository   the quest repository; must not be null
     * @param itemRewardService grants a quest's optional item reward, or {@code null} to disable item
     *                          rewards (leaving gold/XP/title behaviour unchanged)
     */
    public QuestKillService(QuestRepository questRepository, QuestItemRewardService itemRewardService) {
        this(questRepository, itemRewardService, null);
    }

    /**
     * Creates a kill-quest service that additionally grants configured item and reputation rewards on
     * completion.
     *
     * @param questRepository         the quest repository; must not be null
     * @param itemRewardService       grants a quest's optional item reward, or {@code null} to disable
     *                                item rewards
     * @param reputationRewardService applies a quest's optional reputation reward, or {@code null} to
     *                                disable reputation rewards
     */
    public QuestKillService(QuestRepository questRepository, QuestItemRewardService itemRewardService,
            QuestReputationRewardService reputationRewardService) {
        this.questRepository = Objects.requireNonNull(questRepository, "questRepository is required");
        this.itemRewardService = itemRewardService;
        this.reputationRewardService = reputationRewardService;
    }

    /**
     * Registers the achievement service that unlocks quest-milestone achievements (e.g. "Errand
     * Runner" at 5 completed quests) when a one-time quest is completed via this service.
     *
     * @param achievementService the achievement service; may be null to disable achievement unlocks
     */
    public void setAchievementService(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    /**
     * Registers the level-up service used to award quest XP and apply class-differentiated vitals
     * gains on completion. When not set, a service applying the legacy default gains to every class
     * is used.
     *
     * @param levelUpService the level-up service; must not be null
     */
    public void setLevelUpService(LevelUpService levelUpService) {
        this.levelUpService = Objects.requireNonNull(levelUpService, "levelUpService is required");
    }

    /**
     * Records a mob kill for the player and returns an updated player together
     * with any progress messages.
     *
     * <p>The player's story-quest slot ({@link Player#getActiveQuest()}) and daily-quest slot
     * ({@link Player#getActiveDailyQuest()}) are checked independently, so a single kill can
     * progress either or both without conflating the two. Returns empty when neither held quest
     * targets the killed mob.
     *
     * @param player          the attacking player; must not be null
     * @param killedMobId     the template id of the mob that was just killed
     * @return a {@link KillResult} with the updated player and messages, or empty
     */
    public Optional<KillResult> recordKill(Player player, String killedMobId) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(killedMobId, "killedMobId is required");

        List<String> messages = new ArrayList<>();
        Player current = player;
        current = recordKillForSlot(current, killedMobId, false, messages);
        current = recordKillForSlot(current, killedMobId, true, messages);

        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new KillResult(current, List.copyOf(messages)));
    }

    /**
     * Applies a kill to a single quest slot (story or daily), appending any progress/completion
     * message to {@code messages} and returning the player with that slot decremented. Returns the
     * player unchanged when the slot is empty or its quest does not target the killed mob.
     *
     * @param player      the player whose slot is being checked; must not be null
     * @param killedMobId the id of the mob that was just killed; must not be null
     * @param dailySlot   {@code true} to check the daily slot, {@code false} for the story slot
     * @param messages    the mutable message list to append to; must not be null
     * @return the player, updated when the slot progressed
     */
    private Player recordKillForSlot(Player player, String killedMobId, boolean dailySlot, List<String> messages) {
        ActiveQuest active = dailySlot ? player.getActiveDailyQuest() : player.getActiveQuest();
        if (active == null) {
            return player;
        }

        QuestTemplate template;
        try {
            template = questRepository.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest template {}: {}", active.templateId(), e.getMessage());
            return player;
        }

        if (template == null || template.isDeliveryQuest() || template.targetMobId() == null) {
            return player;
        }

        if (!template.targetMobId().equals(killedMobId)) {
            return player;
        }

        ActiveQuest updated = active.decrementKills();
        Player updatedPlayer = dailySlot
            ? player.withActiveDailyQuest(updated)
            : player.withActiveQuest(updated);

        if (updated.isComplete()) {
            if (template.isDaily()) {
                messages.add("Daily quest complete! Use DAILY_QUEST COMPLETE to claim your reward.");
            } else {
                messages.add("You have fulfilled your contract. Return to the Guild Clerk to claim your reward.");
            }
        } else {
            int done = template.requiredKills() - updated.killsRemaining();
            messages.add(template.name() + ": " + done + "/" + template.requiredKills() + " kills.");
        }

        return updatedPlayer;
    }

    /**
     * Grants the gold, XP, and any title reward for completing a finished kill quest.
     *
     * <p>Callers are responsible for validating that the quest is actually complete
     * (i.e. {@code player.getActiveQuest().isComplete()}) and clearing/leaving the
     * active quest as needed before calling this method; this method itself always
     * clears the active quest as part of granting the reward.
     *
     * @param player   the player completing the quest; must not be null
     * @param template the quest template being completed; must not be null
     * @return a {@link CompletionResult} bundling the updated player, whether the
     *     player leveled up, and messages describing the reward
     */
    public CompletionResult grantCompletionReward(Player player, QuestTemplate template) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(template, "template is required");

        Player rewarded = player.withActiveQuest(null).addGold(template.goldReward());
        LevelUpService.LevelUpResult lvResult = levelUpService.awardXp(rewarded, template.xpReward());
        rewarded = lvResult.player();

        ItemRewardGrant itemGrant = itemRewardService != null
            ? itemRewardService.grant(rewarded, template)
            : ItemRewardGrant.none(rewarded);
        rewarded = itemGrant.player();

        List<String> messages = new ArrayList<>();
        messages.add("The Guild Clerk nods approvingly. Contract complete: " + template.name() + ".");
        messages.add(QuestItemRewardService.receiveLine(
            template.goldReward(), template.xpReward(), itemGrant.description()));
        messages.addAll(itemGrant.messages());

        ReputationRewardGrant reputationGrant = reputationRewardService != null
            ? reputationRewardService.grant(rewarded, template)
            : ReputationRewardGrant.none(rewarded);
        rewarded = reputationGrant.player();
        reputationGrant.messageText().ifPresent(messages::add);

        String titleReward = template.titleReward();
        if (titleReward != null && !rewarded.titles().has(titleReward)) {
            rewarded = rewarded.grantTitle(titleReward);
            messages.add("You have earned the title: " + titleReward + "!");
        }

        if (!template.isRepeatable()) {
            rewarded = rewarded.withCompletedQuest(template.id());
            rewarded = unlockQuestAchievements(rewarded, messages);
        }

        return new CompletionResult(
            rewarded, lvResult.leveledUp(), List.copyOf(messages), itemGrant.droppedItems());
    }

    /**
     * Checks and unlocks any milestone achievements the just-completed one-time quest satisfied,
     * appending an {@code "Achievement unlocked: <name>!"} line to {@code messages} per new unlock.
     * Returns the player unchanged when no achievement service is configured or nothing new unlocked.
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

    /**
     * Result of a quest kill record operation.
     *
     * @param player   the updated player with decremented quest counter
     * @param messages progress messages to deliver to the player
     */
    public record KillResult(Player player, List<String> messages) {}

    /**
     * Result of granting a kill-quest completion reward.
     *
     * @param player       the updated player with reward applied and active quest cleared
     * @param leveledUp    {@code true} when the XP reward caused a level-up
     * @param messages     messages describing the reward (and any title earned)
     * @param droppedItems item-reward copies that did not fit and must be dropped at the player's feet
     *                     by the caller; never null, empty when none overflowed
     */
    public record CompletionResult(
        Player player, boolean leveledUp, List<String> messages, List<Item> droppedItems) {

        public CompletionResult {
            droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
        }
    }
}
