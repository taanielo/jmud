package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ActiveQuest} exploration progress tracking.
 */
class ActiveQuestTest {

    private static final QuestId ID = QuestId.of("explore-catacombs");

    @Test
    void convenienceConstructorStartsWithNoVisitedRooms() {
        ActiveQuest quest = new ActiveQuest(ID, 0);
        assertTrue(quest.visitedRoomIds().isEmpty());
    }

    @Test
    void withVisitedRoomRecordsRoomLowerCased() {
        ActiveQuest quest = new ActiveQuest(ID, 0).withVisitedRoom("Catacombs-Entrance");
        assertTrue(quest.hasVisited("catacombs-entrance"));
        assertEquals(List.of("catacombs-entrance"), quest.visitedRoomIds());
    }

    @Test
    void withVisitedRoomIsIdempotent() {
        ActiveQuest quest = new ActiveQuest(ID, 0)
            .withVisitedRoom("ossuary-hall")
            .withVisitedRoom("ossuary-hall");
        assertEquals(1, quest.visitedRoomIds().size());
    }

    @Test
    void decrementKillsPreservesVisitedRooms() {
        ActiveQuest quest = new ActiveQuest(ID, 3, List.of("ossuary-hall")).decrementKills();
        assertEquals(2, quest.killsRemaining());
        assertTrue(quest.hasVisited("ossuary-hall"));
    }

    @Test
    void hasVisitedIsFalseForUnvisitedRoom() {
        ActiveQuest quest = new ActiveQuest(ID, 0).withVisitedRoom("ossuary-hall");
        assertFalse(quest.hasVisited("burial-alcove"));
    }
}
