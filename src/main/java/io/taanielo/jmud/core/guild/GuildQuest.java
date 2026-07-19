package io.taanielo.jmud.core.guild;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The active cooperative guild quest for one guild: a shared "slay N {@code targetName}" objective
 * whose progress accrues automatically from every online member's kills of the matching mob type.
 *
 * <p>Instances are immutable and self-describing (they embed the assigned objective's target, required
 * kills and reward) so that rendering, completion crediting and persistence never need to re-resolve
 * the {@link GuildQuestPool}. Use {@link #recordKill()} to advance progress; the counter clamps at
 * {@link #requiredKills()} so a completed quest can never be over-credited.
 *
 * @param questId       the {@link GuildQuestObjective#questId()} this progress belongs to
 * @param name          display name of the objective
 * @param targetMobId   the mob template id whose kills advance this quest
 * @param targetName    plural display noun used in the progress line (e.g. "dire wolves")
 * @param requiredKills the number of matching kills needed to complete the quest (positive)
 * @param currentKills  the number of matching kills recorded so far (0..requiredKills)
 * @param goldReward    the gold paid into the guild treasury on completion (non-negative)
 */
public record GuildQuest(
    String questId,
    String name,
    String targetMobId,
    String targetName,
    int requiredKills,
    int currentKills,
    int goldReward
) {

    @JsonCreator
    public GuildQuest(
        @JsonProperty("questId") String questId,
        @JsonProperty("name") String name,
        @JsonProperty("targetMobId") String targetMobId,
        @JsonProperty("targetName") String targetName,
        @JsonProperty("requiredKills") int requiredKills,
        @JsonProperty("currentKills") int currentKills,
        @JsonProperty("goldReward") int goldReward
    ) {
        this.questId = Objects.requireNonNull(questId, "questId is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.targetMobId = Objects.requireNonNull(targetMobId, "targetMobId is required");
        this.targetName = Objects.requireNonNull(targetName, "targetName is required");
        if (requiredKills <= 0) {
            throw new IllegalArgumentException("requiredKills must be positive");
        }
        if (currentKills < 0) {
            throw new IllegalArgumentException("currentKills must not be negative");
        }
        this.requiredKills = requiredKills;
        this.currentKills = Math.min(currentKills, requiredKills);
        this.goldReward = goldReward;
    }

    /**
     * Creates a fresh, zero-progress guild quest from a pool objective.
     *
     * @param objective the objective to assign; must not be null
     * @return the newly assigned guild quest with no kills recorded yet
     */
    public static GuildQuest fromObjective(GuildQuestObjective objective) {
        Objects.requireNonNull(objective, "objective is required");
        return new GuildQuest(
            objective.questId(),
            objective.name(),
            objective.targetMobId(),
            objective.targetName(),
            objective.requiredKills(),
            0,
            objective.goldReward());
    }

    /**
     * Returns a copy of this quest with one additional kill recorded, clamped so {@link #currentKills()}
     * never exceeds {@link #requiredKills()}.
     *
     * @return the advanced guild quest
     */
    public GuildQuest recordKill() {
        return new GuildQuest(
            questId, name, targetMobId, targetName, requiredKills,
            Math.min(requiredKills, currentKills + 1), goldReward);
    }

    /** Returns {@code true} when every required kill has been recorded and the reward is due. */
    @JsonIgnore
    public boolean isComplete() {
        return currentKills >= requiredKills;
    }

    /**
     * Returns the player-facing progress line, e.g. {@code "Slayed 7 / 20 dire wolves"}.
     *
     * @return the progress description
     */
    @JsonIgnore
    public String progressLine() {
        return "Slayed " + currentKills + " / " + requiredKills + " " + targetName;
    }
}
