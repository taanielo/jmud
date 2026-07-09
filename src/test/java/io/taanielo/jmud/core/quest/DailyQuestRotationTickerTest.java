package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.WorldClock;

/**
 * Unit tests for {@link DailyQuestRotationTicker} covering day-transition detection and broadcast.
 */
class DailyQuestRotationTickerTest {

    /** Records global broadcasts so the test can assert on rotation notices. */
    private static final class RecordingBroadcaster implements MessageBroadcaster {
        private final List<Message> globalMessages = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            globalMessages.add(message);
        }
    }

    private WorldClock worldClock;
    private DailyQuestService dailyQuestService;
    private RecordingBroadcaster broadcaster;
    private DailyQuestRotationTicker ticker;

    @BeforeEach
    void setUp() {
        // One tick per phase: DAY -> NIGHT -> DAY within two ticks.
        worldClock = new WorldClock(1);
        DailyQuestPool pool = new DailyQuestPool("slayer", "Daily Slayer", List.of(
            new QuestTemplate(QuestId.of("slayer-a"), "A", "rat", "rat", 5, 50, 100, "slayer"),
            new QuestTemplate(QuestId.of("slayer-b"), "B", "goblin", "goblin", 6, 75, 150, "slayer")
        ));
        dailyQuestService = new DailyQuestService(List.of(pool));
        broadcaster = new RecordingBroadcaster();
        ticker = new DailyQuestRotationTicker(worldClock, dailyQuestService, broadcaster);
    }

    /** Simulates one world tick with the registry ordering: clock first, then rotation ticker. */
    private void advanceOneTick() {
        worldClock.tick();
        ticker.tick();
    }

    @Test
    void doesNotRotateOnDayToNightTransition() {
        // First tick flips DAY -> NIGHT, which is not a new day.
        advanceOneTick();
        assertEquals("slayer-a", dailyQuestService.getActiveDailyQuest("slayer").orElseThrow().id().getValue());
        assertTrue(broadcaster.globalMessages.isEmpty());
    }

    @Test
    void rotatesAndBroadcastsOnNightToDayTransition() {
        advanceOneTick(); // DAY -> NIGHT
        advanceOneTick(); // NIGHT -> DAY (new day)

        assertEquals("slayer-b", dailyQuestService.getActiveDailyQuest("slayer").orElseThrow().id().getValue());
        assertEquals(1, broadcaster.globalMessages.size());
    }

    @Test
    void rotatesOncePerNewDay() {
        // Four ticks = two full day/night cycles = two night->day transitions.
        advanceOneTick(); // DAY -> NIGHT
        advanceOneTick(); // NIGHT -> DAY (rotate 1)
        advanceOneTick(); // DAY -> NIGHT
        advanceOneTick(); // NIGHT -> DAY (rotate 2)

        assertEquals(2, dailyQuestService.rotationCounter());
        assertEquals(2, broadcaster.globalMessages.size());
        // Two rotations over a 2-variant pool wraps back to the first variant.
        assertEquals("slayer-a", dailyQuestService.getActiveDailyQuest("slayer").orElseThrow().id().getValue());
    }
}
