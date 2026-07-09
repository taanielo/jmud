package io.taanielo.jmud.core.quest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link QuestRepository} that resolves normal Guild Clerk contracts from a primary repository and
 * additionally resolves daily quest variants by id from a {@link DailyQuestService}.
 *
 * <p>{@link #findAll()} intentionally returns only the primary (non-daily) contracts, so daily
 * quests never appear in the {@code QUEST LIST} handed out by the Guild Clerk — they are surfaced
 * exclusively through the {@code DAILY_QUEST} command. {@link #findById(QuestId)} however also looks
 * up daily variants, so an accepted daily quest can still be resolved for kill-progress tracking and
 * status/completion once the player is holding it.
 */
public class CompositeQuestRepository implements QuestRepository {

    private final QuestRepository primary;
    private final DailyQuestService dailyQuestService;

    /**
     * Creates a composite repository.
     *
     * @param primary           the repository of normal Guild Clerk contracts; must not be null
     * @param dailyQuestService the daily quest service supplying rotating quest variants; must not be null
     */
    public CompositeQuestRepository(QuestRepository primary, DailyQuestService dailyQuestService) {
        this.primary = Objects.requireNonNull(primary, "primary is required");
        this.dailyQuestService = Objects.requireNonNull(dailyQuestService, "dailyQuestService is required");
    }

    @Override
    public List<QuestTemplate> findAll() throws QuestRepositoryException {
        return primary.findAll();
    }

    @Override
    public Optional<QuestTemplate> findById(QuestId id) throws QuestRepositoryException {
        Objects.requireNonNull(id, "id is required");
        Optional<QuestTemplate> fromPrimary = primary.findById(id);
        if (fromPrimary.isPresent()) {
            return fromPrimary;
        }
        return dailyQuestService.findQuestById(id);
    }
}
