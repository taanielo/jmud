package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.quest.QuestItemRewardService.ItemRewardGrant;

/**
 * Domain service governing the daily quest rotation.
 *
 * <p>Each {@link DailyQuestPool} exposes exactly one <em>active</em> quest at a time. The active
 * quest is chosen deterministically from a single {@link #rotationCounter}: every game day the
 * counter advances by one (via {@link #rotate()}, driven by {@link DailyQuestRotationTicker}) and
 * each pool maps that counter, modulo its own size, to a variant. Because the mapping is a pure
 * function of the counter and never reads the wall clock, the rotation is reproducible across runs
 * and tick rates (AGENTS.md §5).
 *
 * <p>The counter is the only mutable state; it is confined to the tick thread for writes (rotation
 * happens on the tick loop) while reads are safe from any thread as a point-in-time snapshot.
 * Completion reward accounting is pure and delegates to {@link LevelUpService} and
 * {@link Player#addGold(int)}, reusing the same reward path as {@link QuestKillService}.
 */
public class DailyQuestService {

    private final Map<String, DailyQuestPool> poolsById = new LinkedHashMap<>();
    private final Map<QuestId, QuestTemplate> questsById = new LinkedHashMap<>();
    private final AtomicLong rotationCounter = new AtomicLong();
    private final LevelUpService levelUpService = new LevelUpService();
    private final QuestItemRewardService itemRewardService;

    /**
     * Creates a daily quest service over the given pools, starting at rotation index zero.
     *
     * @param pools the daily quest pools; must not be null and must have unique pool ids
     */
    public DailyQuestService(List<DailyQuestPool> pools) {
        this(pools, null);
    }

    /**
     * Creates a daily quest service that additionally grants configured item rewards on completion.
     *
     * @param pools             the daily quest pools; must not be null and must have unique pool ids
     * @param itemRewardService grants a quest's optional item reward, or {@code null} to disable item
     *                          rewards
     */
    public DailyQuestService(List<DailyQuestPool> pools, QuestItemRewardService itemRewardService) {
        this.itemRewardService = itemRewardService;
        Objects.requireNonNull(pools, "pools is required");
        for (DailyQuestPool pool : pools) {
            if (poolsById.putIfAbsent(pool.poolId(), pool) != null) {
                throw new IllegalArgumentException("Duplicate daily quest pool id: " + pool.poolId());
            }
            for (QuestTemplate quest : pool.quests()) {
                if (questsById.putIfAbsent(quest.id(), quest) != null) {
                    throw new IllegalArgumentException("Duplicate daily quest id: " + quest.id().getValue());
                }
            }
        }
    }

    /**
     * Returns the ids of all configured daily quest pools, in configuration order.
     */
    public Set<String> poolIds() {
        return Set.copyOf(poolsById.keySet());
    }

    /**
     * Returns the quest variant currently active for the given pool, if the pool exists.
     *
     * @param poolId the pool id to query; must not be null
     * @return the active quest variant, or empty when no such pool is configured
     */
    public Optional<QuestTemplate> getActiveDailyQuest(String poolId) {
        Objects.requireNonNull(poolId, "poolId is required");
        DailyQuestPool pool = poolsById.get(poolId);
        if (pool == null) {
            return Optional.empty();
        }
        return Optional.of(pool.variantAt(rotationCounter.get()));
    }

    /**
     * Returns the active quest variant for every configured pool, in configuration order.
     */
    public List<QuestTemplate> activeDailyQuests() {
        long counter = rotationCounter.get();
        List<QuestTemplate> active = new ArrayList<>(poolsById.size());
        for (DailyQuestPool pool : poolsById.values()) {
            active.add(pool.variantAt(counter));
        }
        return List.copyOf(active);
    }

    /**
     * Looks up any daily quest variant (active or not) by its id. Used so mob-kill progress and
     * completion can resolve an accepted daily quest even after the pool has rotated past it.
     *
     * @param id the quest id to look up; must not be null
     * @return the matching daily quest variant, or empty
     */
    public Optional<QuestTemplate> findQuestById(QuestId id) {
        Objects.requireNonNull(id, "id is required");
        return Optional.ofNullable(questsById.get(id));
    }

    /**
     * Advances the rotation by one game day, making the next variant in each pool active.
     *
     * <p>Must only be called on the tick thread.
     *
     * @return the newly active quest variant of every pool after the rotation
     */
    public List<QuestTemplate> rotate() {
        rotationCounter.incrementAndGet();
        return activeDailyQuests();
    }

    /**
     * Returns the current rotation counter value (the number of day transitions applied).
     */
    public long rotationCounter() {
        return rotationCounter.get();
    }

    /**
     * Grants the completion reward for a finished daily quest and clears the player's active quest.
     *
     * <p>Validates that the player is currently on the given daily quest and that it is complete;
     * on any failure the returned result carries {@code success == false}, the unchanged player, and
     * an explanatory message.
     *
     * @param player  the player claiming the reward; must not be null
     * @param questId the id of the daily quest being completed; must not be null
     * @return a {@link DailyQuestCompletionResult} describing the outcome
     */
    public DailyQuestCompletionResult completeDailyQuest(Player player, QuestId questId) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(questId, "questId is required");

        QuestTemplate template = questsById.get(questId);
        if (template == null || !template.isDaily()) {
            return DailyQuestCompletionResult.failure(player, "That is not a daily quest.");
        }
        ActiveQuest active = player.getActiveQuest();
        if (active == null || !active.templateId().equals(questId)) {
            return DailyQuestCompletionResult.failure(player, "You are not currently on that daily quest.");
        }
        if (!active.isComplete()) {
            return DailyQuestCompletionResult.failure(player,
                "Daily quest not yet complete: " + template.name()
                    + " (" + active.killsRemaining() + " kills remaining).");
        }

        Player rewarded = player.withActiveQuest(null).addGold(template.goldReward());
        LevelUpService.LevelUpResult lvResult = levelUpService.awardXp(rewarded, template.xpReward());
        rewarded = lvResult.player();

        ItemRewardGrant itemGrant = itemRewardService != null
            ? itemRewardService.grant(rewarded, template)
            : ItemRewardGrant.none(rewarded);
        rewarded = itemGrant.player();

        List<String> messages = new ArrayList<>();
        messages.add("Daily quest complete: " + template.name() + "!");
        messages.add(dailyBonusLine(template.goldReward(), template.xpReward(), itemGrant.description()));
        messages.addAll(itemGrant.messages());

        String titleReward = template.titleReward();
        if (titleReward != null && !rewarded.titles().has(titleReward)) {
            rewarded = rewarded.grantTitle(titleReward);
            messages.add("You have earned the title: " + titleReward + "!");
        }

        return DailyQuestCompletionResult.success(
            rewarded, lvResult.leveledUp(), List.copyOf(messages), itemGrant.droppedItems());
    }

    private static String dailyBonusLine(int gold, int xp, String itemDescription) {
        if (itemDescription == null) {
            return "You receive a daily bonus of " + gold + " gold and " + xp + " experience.";
        }
        return "You receive a daily bonus of " + gold + " gold, " + xp
            + " experience, and " + itemDescription + ".";
    }
}
