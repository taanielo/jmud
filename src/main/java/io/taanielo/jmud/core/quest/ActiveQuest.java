package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the current state of a player's active quest contract.
 *
 * <p>Instances are immutable; use {@link #decrementKills()} to produce a new instance with the
 * kill counter reduced by one, or {@link #withVisitedRoom(String)} to record a visited room for
 * exploration quests.
 *
 * @param templateId      the quest template this progress belongs to
 * @param killsRemaining  number of target mob kills still needed (kill quests only)
 * @param visitedRoomIds  ids of the required rooms already visited (exploration quests only);
 *                        never {@code null}, stored lower-cased and de-duplicated
 */
public record ActiveQuest(QuestId templateId, int killsRemaining, List<String> visitedRoomIds) {

    @JsonCreator
    public ActiveQuest(
        @JsonProperty("templateId") QuestId templateId,
        @JsonProperty("killsRemaining") int killsRemaining,
        @JsonProperty("visitedRoomIds") List<String> visitedRoomIds
    ) {
        this.templateId = Objects.requireNonNull(templateId, "templateId is required");
        if (killsRemaining < 0) {
            throw new IllegalArgumentException("killsRemaining must be non-negative");
        }
        this.killsRemaining = killsRemaining;
        this.visitedRoomIds = visitedRoomIds == null ? List.of() : List.copyOf(visitedRoomIds);
    }

    /**
     * Creates an active quest with no visited rooms recorded.
     *
     * @param templateId     the quest template this progress belongs to
     * @param killsRemaining number of target mob kills still needed
     */
    public ActiveQuest(QuestId templateId, int killsRemaining) {
        this(templateId, killsRemaining, List.of());
    }

    /**
     * Returns a new {@link ActiveQuest} with {@code killsRemaining} reduced by one.
     * The counter will not go below zero.
     */
    public ActiveQuest decrementKills() {
        return new ActiveQuest(templateId, Math.max(0, killsRemaining - 1), visitedRoomIds);
    }

    /**
     * Returns a new {@link ActiveQuest} with the given room id recorded as visited. Room ids are
     * normalised to lower case and de-duplicated, so recording an already-visited room returns an
     * equivalent instance.
     *
     * @param roomId the room id that was just entered; must not be null
     * @return an active quest whose {@link #visitedRoomIds()} contains {@code roomId}
     */
    public ActiveQuest withVisitedRoom(String roomId) {
        Objects.requireNonNull(roomId, "roomId is required");
        String normalized = roomId.toLowerCase(Locale.ROOT);
        if (visitedRoomIds.contains(normalized)) {
            return this;
        }
        List<String> updated = new ArrayList<>(visitedRoomIds);
        updated.add(normalized);
        return new ActiveQuest(templateId, killsRemaining, updated);
    }

    /**
     * Returns {@code true} when the given room id has already been recorded as visited.
     *
     * @param roomId the room id to check; must not be null
     * @return {@code true} when the room has been visited
     */
    public boolean hasVisited(String roomId) {
        Objects.requireNonNull(roomId, "roomId is required");
        return visitedRoomIds.contains(roomId.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} when all required kills have been recorded and the
     * reward is ready to be claimed.
     */
    public boolean isComplete() {
        return killsRemaining == 0;
    }
}
