package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.quest.QuestItemRewardService.ItemRewardGrant;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Domain service for exploration quests: the player must visit every room listed in the quest's
 * {@code requiredRoomIds}. Each time the player enters a required room that has not yet been
 * recorded, their progress advances; entering the final required room auto-completes the quest and
 * grants gold, XP and an optional title.
 *
 * <p>Room visits are deterministic — the same player entering the same room with the same active
 * quest always yields the same progress. The service is stateless and thread-safe; all mutation is
 * expressed as new immutable {@link Player} snapshots returned to the (tick-thread) caller.
 */
@Slf4j
public class ExplorationQuestService {

    private final QuestRepository questRepository;
    private final LevelUpService levelUpService;
    private final QuestItemRewardService itemRewardService;

    public ExplorationQuestService(QuestRepository questRepository) {
        this(questRepository, null);
    }

    /**
     * Creates an exploration service that additionally grants configured item rewards on completion.
     *
     * @param questRepository   the quest repository; must not be null
     * @param itemRewardService grants a quest's optional item reward, or {@code null} to disable item
     *                          rewards
     */
    public ExplorationQuestService(QuestRepository questRepository, QuestItemRewardService itemRewardService) {
        this.questRepository = Objects.requireNonNull(questRepository, "questRepository is required");
        this.levelUpService = new LevelUpService();
        this.itemRewardService = itemRewardService;
    }

    /**
     * Records the player entering the given room against their active exploration quest.
     *
     * <p>Returns empty when the player has no active exploration quest, the entered room is not one
     * of the quest's required rooms, or the room has already been recorded — in those cases nothing
     * changes. Otherwise the visit is recorded and an {@link ExplorationQuestResult} is returned:
     * a progress result when rooms still remain, or a completion result (reward applied, quest
     * cleared) when the final required room was entered.
     *
     * @param player the player who just entered the room; must not be null
     * @param roomId the room the player entered; must not be null
     * @return the outcome of recording the visit, or empty when nothing changed
     */
    public Optional<ExplorationQuestResult> recordRoomVisit(Player player, RoomId roomId) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(roomId, "roomId is required");

        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            return Optional.empty();
        }

        QuestTemplate template;
        try {
            template = questRepository.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest template on room visit {}: {}", active.templateId(), e.getMessage());
            return Optional.empty();
        }
        if (template == null || !template.isExplorationQuest()) {
            return Optional.empty();
        }

        String entered = roomId.getValue().toLowerCase(Locale.ROOT);
        boolean isRequired = template.requiredRoomIds().stream()
            .anyMatch(id -> id.equalsIgnoreCase(entered));
        if (!isRequired || active.hasVisited(entered)) {
            return Optional.empty();
        }

        ActiveQuest updatedQuest = active.withVisitedRoom(entered);
        int visited = updatedQuest.visitedRoomIds().size();
        int total = template.requiredRoomIds().size();

        if (visited >= total) {
            return Optional.of(complete(player, template));
        }

        Player updatedPlayer = player.withActiveQuest(updatedQuest);
        List<String> messages = new ArrayList<>();
        messages.add(template.name() + ": explored " + visited + " of " + total + " rooms.");
        return Optional.of(ExplorationQuestResult.progress(updatedPlayer, messages));
    }

    private ExplorationQuestResult complete(Player player, QuestTemplate template) {
        Player rewarded = player.withActiveQuest(null).addGold(template.goldReward());
        LevelUpService.LevelUpResult lvResult = levelUpService.awardXp(rewarded, template.xpReward());
        rewarded = lvResult.player();

        ItemRewardGrant itemGrant = itemRewardService != null
            ? itemRewardService.grant(rewarded, template)
            : ItemRewardGrant.none(rewarded);
        rewarded = itemGrant.player();

        List<String> messages = new ArrayList<>();
        messages.add("You have explored every corner of your charge. Quest complete: " + template.name() + ".");
        messages.add(QuestItemRewardService.receiveLine(
            template.goldReward(), template.xpReward(), itemGrant.description()));
        messages.addAll(itemGrant.messages());
        if (lvResult.leveledUp()) {
            messages.add("You have advanced to level " + rewarded.getLevel() + "!");
        }
        String titleReward = template.titleReward();
        if (titleReward != null && !rewarded.titles().has(titleReward)) {
            rewarded = rewarded.grantTitle(titleReward);
            messages.add("You have earned the title: " + titleReward + "!");
        }
        return ExplorationQuestResult.completed(rewarded, lvResult.leveledUp(), messages, itemGrant.droppedItems());
    }
}
