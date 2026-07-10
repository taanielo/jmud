package io.taanielo.jmud.core.player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.taanielo.jmud.core.quest.QuestId;

/**
 * Tracks the set of one-time quest contracts a player has already completed.
 *
 * <p>Only quests declared {@code repeatable: false} are recorded here; repeatable contracts (the
 * default, including daily quests) are never added. The set backs two gameplay rules: a one-time
 * quest already present here cannot be accepted again, and a quest whose {@code prerequisiteQuestId}
 * is not present here cannot be accepted at all. It is persisted on the {@link Player} as
 * {@code completedQuests} so story progress survives logout/login, defaulting to empty for existing
 * save files.
 *
 * <p>The set preserves insertion order (first-completed first) so serialization is deterministic,
 * which keeps save files stable and tests reproducible.
 */
public class PlayerCompletedQuests {

    private final Set<QuestId> completed;

    /**
     * Creates a completed-quests component from the given quest-id strings. {@code null} entries and
     * blanks are ignored; duplicates collapse. A {@code null} list yields an empty component.
     *
     * @param questIds the completed quest id strings, in first-completed order
     */
    public PlayerCompletedQuests(List<String> questIds) {
        Set<QuestId> parsed = new LinkedHashSet<>();
        if (questIds != null) {
            for (String questId : questIds) {
                if (questId != null && !questId.isBlank()) {
                    parsed.add(QuestId.of(questId));
                }
            }
        }
        this.completed = parsed;
    }

    private PlayerCompletedQuests(Set<QuestId> completed) {
        this.completed = completed;
    }

    /**
     * Returns an empty completed-quests component (no one-time quests completed).
     */
    public static PlayerCompletedQuests empty() {
        return new PlayerCompletedQuests(new LinkedHashSet<>());
    }

    /**
     * Returns an unmodifiable view of the completed quest ids, in first-completed order.
     */
    public Set<QuestId> completed() {
        return Set.copyOf(completed);
    }

    /**
     * Returns whether the player has completed the given one-time quest.
     *
     * @param questId the quest to test; must not be null
     * @return {@code true} if the quest is in the completed set
     */
    public boolean hasCompleted(QuestId questId) {
        Objects.requireNonNull(questId, "Quest id is required");
        return completed.contains(questId);
    }

    /**
     * Returns how many distinct one-time quests the player has completed.
     */
    public int count() {
        return completed.size();
    }

    /**
     * Returns a copy of this component with the given quest marked completed, or this instance
     * unchanged when the quest was already recorded.
     *
     * @param questId the completed quest; must not be null
     */
    public PlayerCompletedQuests withCompleted(QuestId questId) {
        Objects.requireNonNull(questId, "Quest id is required");
        if (completed.contains(questId)) {
            return this;
        }
        Set<QuestId> next = new LinkedHashSet<>(completed);
        next.add(questId);
        return new PlayerCompletedQuests(next);
    }

    /**
     * Returns the completed quests as an ordered list of their id strings, for JSON persistence.
     */
    public List<String> toIdList() {
        return completed.stream().map(QuestId::getValue).toList();
    }
}
