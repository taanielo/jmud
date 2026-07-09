package io.taanielo.jmud.core.quest;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.TimeOfDay;
import io.taanielo.jmud.core.world.WorldClock;

/**
 * World-level {@link Tickable} that rotates the daily quest pools when a new game day begins.
 *
 * <p>The {@link WorldClock} flips deterministically between {@link TimeOfDay#DAY} and
 * {@link TimeOfDay#NIGHT}; a new day is defined as the transition from {@code NIGHT} back to
 * {@code DAY}. On that boundary this ticker advances {@link DailyQuestService#rotate()} and
 * broadcasts a notice to every online player. Rotation is driven purely by tick-count-derived time,
 * never the wall clock (AGENTS.md §5).
 *
 * <p>Register this ticker <em>after</em> the {@link WorldClock} in the tick registry so that on the
 * boundary tick the clock has already flipped to {@code DAY} before this ticker observes it.
 */
public class DailyQuestRotationTicker implements Tickable {

    private final WorldClock worldClock;
    private final DailyQuestService dailyQuestService;
    private final MessageBroadcaster messageBroadcaster;
    private TimeOfDay previousTimeOfDay;

    /**
     * Creates the rotation ticker.
     *
     * @param worldClock         the deterministic day/night clock whose transitions drive rotation
     * @param dailyQuestService  the service whose pools are rotated on each new day
     * @param messageBroadcaster the sanctioned fan-out used to notify online players
     */
    public DailyQuestRotationTicker(
        WorldClock worldClock,
        DailyQuestService dailyQuestService,
        MessageBroadcaster messageBroadcaster
    ) {
        this.worldClock = Objects.requireNonNull(worldClock, "worldClock is required");
        this.dailyQuestService = Objects.requireNonNull(dailyQuestService, "dailyQuestService is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "messageBroadcaster is required");
        this.previousTimeOfDay = worldClock.timeOfDay();
    }

    /**
     * Detects a night-to-day transition and, when one occurs, rotates the daily quest pools and
     * announces the new daily quests to all online players. Must only be called on the tick thread.
     */
    @Override
    public void tick() {
        TimeOfDay current = worldClock.timeOfDay();
        boolean newDay = previousTimeOfDay == TimeOfDay.NIGHT && current == TimeOfDay.DAY;
        previousTimeOfDay = current;
        if (!newDay) {
            return;
        }
        List<QuestTemplate> active = dailyQuestService.rotate();
        if (active.isEmpty()) {
            return;
        }
        messageBroadcaster.broadcastGlobal(
            new PlainTextMessage("A new day dawns. New daily quests are available! Use DAILY_QUEST to see them."),
            Set.of());
    }
}
