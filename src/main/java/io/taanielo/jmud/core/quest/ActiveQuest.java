package io.taanielo.jmud.core.quest;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the current state of a player's active quest contract.
 *
 * <p>Instances are immutable; use {@link #decrementKills()} to produce a
 * new instance with the kill counter reduced by one.
 *
 * @param templateId     the quest template this progress belongs to
 * @param killsRemaining number of target mob kills still needed
 */
public record ActiveQuest(QuestId templateId, int killsRemaining) {

    @JsonCreator
    public ActiveQuest(
        @JsonProperty("templateId") QuestId templateId,
        @JsonProperty("killsRemaining") int killsRemaining
    ) {
        this.templateId = Objects.requireNonNull(templateId, "templateId is required");
        if (killsRemaining < 0) {
            throw new IllegalArgumentException("killsRemaining must be non-negative");
        }
        this.killsRemaining = killsRemaining;
    }

    /**
     * Returns a new {@link ActiveQuest} with {@code killsRemaining} reduced by one.
     * The counter will not go below zero.
     */
    public ActiveQuest decrementKills() {
        return new ActiveQuest(templateId, Math.max(0, killsRemaining - 1));
    }

    /**
     * Returns {@code true} when all required kills have been recorded and the
     * reward is ready to be claimed.
     */
    public boolean isComplete() {
        return killsRemaining == 0;
    }
}
