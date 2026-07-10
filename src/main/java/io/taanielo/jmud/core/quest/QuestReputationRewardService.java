package io.taanielo.jmud.core.quest;

import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.player.Player;

/**
 * Shared domain service that applies a quest's optional reputation reward on completion.
 *
 * <p>Every completion-granting quest service ({@link QuestKillService}, {@link QuestDeliveryService},
 * {@link QuestNpcDeliveryService}, {@link ExplorationQuestService} and {@link DailyQuestService})
 * delegates here so the "resolve the faction, shift the player's standing by the configured delta,
 * and phrase the change" logic lives in exactly one place rather than being duplicated five times —
 * mirroring how {@link QuestItemRewardService} centralises item rewards.
 *
 * <p>The service performs no I/O: faction definitions are already snapshotted in memory by
 * {@link ReputationService}, and the standing change is expressed as a new immutable {@link Player}
 * snapshot returned to the (tick-thread) caller (AGENTS.md §5). A quest that references an unknown
 * faction is skipped with a warning so a bad reference never blocks completion.
 */
@Slf4j
public class QuestReputationRewardService {

    private final ReputationService reputationService;

    /**
     * Creates a reputation-reward service backed by the given reputation service.
     *
     * @param reputationService resolves faction ids to {@link Faction} definitions; must not be null
     */
    public QuestReputationRewardService(ReputationService reputationService) {
        this.reputationService = Objects.requireNonNull(reputationService, "reputationService is required");
    }

    /**
     * Applies the reputation reward configured on {@code template} to {@code player}.
     *
     * <p>When the quest has no reputation reward, or the reward faction cannot be resolved, the player
     * is returned unchanged with an empty grant so quest completion is never blocked by a bad
     * reference.
     *
     * @param player   the player completing the quest; must not be null
     * @param template the completed quest template; must not be null
     * @return the outcome bundling the updated player and an optional completion message
     */
    public ReputationRewardGrant grant(Player player, QuestTemplate template) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(template, "template is required");
        if (!template.hasReputationReward()) {
            return ReputationRewardGrant.none(player);
        }
        FactionId factionId = FactionId.of(template.reputationRewardFactionId());
        Faction faction = reputationService.findFaction(factionId).orElse(null);
        if (faction == null) {
            log.warn("Quest {} references unknown reputation faction '{}'; skipping reputation grant.",
                template.id().getValue(), template.reputationRewardFactionId());
            return ReputationRewardGrant.none(player);
        }

        int delta = template.reputationRewardDelta();
        Player updated = player.withReputation(player.reputation().adjust(factionId, delta));
        String verb = delta > 0 ? "risen" : "fallen";
        String message = "Your standing with " + faction.name() + " has " + verb + ".";
        return new ReputationRewardGrant(updated, message);
    }

    /**
     * Returns a short human-readable description of a quest's reputation reward for listing/status
     * previews, e.g. {@code "-10 rep with the Bandit Brotherhood"}. Empty when the quest grants no
     * reputation reward or the faction cannot be resolved.
     *
     * @param template the quest template to describe; must not be null
     * @return the reward description, or empty
     */
    public Optional<String> describeReward(QuestTemplate template) {
        Objects.requireNonNull(template, "template is required");
        if (!template.hasReputationReward()) {
            return Optional.empty();
        }
        Faction faction = reputationService.findFaction(
            FactionId.of(template.reputationRewardFactionId())).orElse(null);
        if (faction == null) {
            return Optional.empty();
        }
        int delta = template.reputationRewardDelta();
        String signed = (delta > 0 ? "+" : "") + delta;
        return Optional.of(signed + " rep with " + faction.name());
    }

    /**
     * Outcome of applying a quest's reputation reward.
     *
     * @param player  the player with the standing change applied (unchanged when nothing was granted)
     * @param message a player-facing completion message describing the change, or {@code null} when
     *                nothing was granted
     */
    public record ReputationRewardGrant(Player player, String message) {

        public ReputationRewardGrant {
            Objects.requireNonNull(player, "player is required");
        }

        static ReputationRewardGrant none(Player player) {
            return new ReputationRewardGrant(player, null);
        }

        /**
         * Returns the completion message, or empty when no reputation reward was granted.
         */
        public Optional<String> messageText() {
            return Optional.ofNullable(message);
        }
    }
}
