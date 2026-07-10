package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

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
     * Records a mob kill for the player and returns an updated player together
     * with any progress messages.
     *
     * <p>Returns empty when the player has no active quest or the killed mob
     * does not match the quest target.
     *
     * @param player          the attacking player; must not be null
     * @param killedMobId     the template id of the mob that was just killed
     * @return a {@link KillResult} with the updated player and messages, or empty
     */
    public Optional<KillResult> recordKill(Player player, String killedMobId) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(killedMobId, "killedMobId is required");

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

        if (template == null) {
            return Optional.empty();
        }

        if (template.isDeliveryQuest() || template.targetMobId() == null) {
            return Optional.empty();
        }

        if (!template.targetMobId().equals(killedMobId)) {
            return Optional.empty();
        }

        ActiveQuest updated = active.decrementKills();
        Player updatedPlayer = player.withActiveQuest(updated);

        List<String> messages = new ArrayList<>();
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

        return Optional.of(new KillResult(updatedPlayer, List.copyOf(messages)));
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
        LevelUpService levelUpService = new LevelUpService();
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
        }

        return new CompletionResult(
            rewarded, lvResult.leveledUp(), List.copyOf(messages), itemGrant.droppedItems());
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
