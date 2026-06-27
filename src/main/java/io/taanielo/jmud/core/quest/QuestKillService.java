package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.Player;

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

    public QuestKillService(QuestRepository questRepository) {
        this.questRepository = Objects.requireNonNull(questRepository, "questRepository is required");
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

        if (!template.targetMobId().equals(killedMobId)) {
            return Optional.empty();
        }

        ActiveQuest updated = active.decrementKills();
        Player updatedPlayer = player.withActiveQuest(updated);

        List<String> messages = new ArrayList<>();
        if (updated.isComplete()) {
            messages.add("You have fulfilled your contract. Return to the Guild Clerk to claim your reward.");
        } else {
            int done = template.requiredKills() - updated.killsRemaining();
            messages.add(template.name() + ": " + done + "/" + template.requiredKills() + " kills.");
        }

        return Optional.of(new KillResult(updatedPlayer, List.copyOf(messages)));
    }

    /**
     * Result of a quest kill record operation.
     *
     * @param player   the updated player with decremented quest counter
     * @param messages progress messages to deliver to the player
     */
    public record KillResult(Player player, List<String> messages) {}
}
