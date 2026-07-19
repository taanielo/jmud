package io.taanielo.jmud.core.guild;

import java.util.Objects;

import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.TimeOfDay;
import io.taanielo.jmud.core.world.WorldClock;

/**
 * World-level {@link Tickable} that rotates every guild's cooperative guild quest when a new game day
 * begins.
 *
 * <p>The {@link WorldClock} flips deterministically between {@link TimeOfDay#DAY} and
 * {@link TimeOfDay#NIGHT}; a new day is the transition from {@code NIGHT} back to {@code DAY}. On that
 * boundary this ticker calls {@link GuildQuestService#rotate()}, which re-rolls each guild's objective
 * and announces it on the {@code [Guild]} channel. Rotation is driven purely by tick-derived time,
 * never the wall clock (AGENTS.md §5), mirroring {@code DailyQuestRotationTicker}.
 *
 * <p>Register this ticker <em>after</em> the {@link WorldClock} so that on the boundary tick the clock
 * has already flipped to {@code DAY} before this ticker observes it.
 */
public class GuildQuestRotationTicker implements Tickable {

    private final WorldClock worldClock;
    private final GuildQuestService guildQuestService;
    private TimeOfDay previousTimeOfDay;

    /**
     * Creates the rotation ticker.
     *
     * @param worldClock        the deterministic day/night clock whose transitions drive rotation
     * @param guildQuestService the service whose guild quests are rotated on each new day
     */
    public GuildQuestRotationTicker(WorldClock worldClock, GuildQuestService guildQuestService) {
        this.worldClock = Objects.requireNonNull(worldClock, "worldClock is required");
        this.guildQuestService = Objects.requireNonNull(guildQuestService, "guildQuestService is required");
        this.previousTimeOfDay = worldClock.timeOfDay();
    }

    /**
     * Detects a night-to-day transition and, when one occurs, rotates every guild's guild quest. Must
     * only be called on the tick thread.
     */
    @Override
    public void tick() {
        TimeOfDay current = worldClock.timeOfDay();
        boolean newDay = previousTimeOfDay == TimeOfDay.NIGHT && current == TimeOfDay.DAY;
        previousTimeOfDay = current;
        if (!newDay) {
            return;
        }
        guildQuestService.rotate();
    }
}
