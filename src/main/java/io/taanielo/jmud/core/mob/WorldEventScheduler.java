package io.taanielo.jmud.core.mob;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.tick.Tickable;

/**
 * World-level {@link Tickable} that opens timed <em>world events</em>: on a randomized tick interval
 * it tears a rare-elite mob (a {@link MobTemplate#worldEvent()} template) into a fixed room in one of
 * the eligible zones and announces the event server-wide, giving players a reason to detour <em>now</em>.
 *
 * <p>Only one event is open at a time. While an event is open the scheduler watches its mob:
 * <ul>
 *   <li>if a player slays it, the {@link MobRegistry} world-boss kill path has already announced the
 *       death and dropped the guaranteed reward, so the scheduler simply clears the slain instance
 *       and rolls the next interval;</li>
 *   <li>if the bounded window elapses with the mob still alive, the scheduler announces the rift
 *       collapsing, purges the mob with no kill credit, and rolls the next interval.</li>
 * </ul>
 *
 * <p>The ticker is pure in-memory state (a countdown and the current event's mob) and mutates only on
 * the tick thread, so it introduces no blocking I/O and no concurrency of its own (AGENTS.md §5).
 * Interval and window timing are drawn from the shared {@link CombatRandom} port so the schedule is
 * reproducible from a world seed and unit-testable as an exact sequence.
 */
@Slf4j
public class WorldEventScheduler implements Tickable {

    private final WorldEventStage stage;
    private final WorldBossAnnouncer announcer;
    private final CombatRandom random;
    private final int minIntervalTicks;
    private final int maxIntervalTicks;
    private final int windowTicks;

    @Nullable
    private MobInstance activeMob;
    private int windowRemaining;
    private int ticksUntilNextEvent;

    /**
     * Creates a world-event scheduler.
     *
     * @param stage            the mob-placement port used to spawn and purge world-event mobs
     * @param announcer        the sanctioned announcer used for the spawn and timeout broadcasts
     * @param random           the shared RNG port used to pick the interval, window and mob
     * @param minIntervalTicks lower bound (in ticks) of the randomized gap between events (>= 1)
     * @param maxIntervalTicks upper bound (in ticks) of the gap between events (>= minIntervalTicks)
     * @param windowTicks      ticks an open event stays killable before it despawns unkilled (>= 1)
     */
    public WorldEventScheduler(
        WorldEventStage stage,
        WorldBossAnnouncer announcer,
        CombatRandom random,
        int minIntervalTicks,
        int maxIntervalTicks,
        int windowTicks
    ) {
        this.stage = Objects.requireNonNull(stage, "stage is required");
        this.announcer = Objects.requireNonNull(announcer, "announcer is required");
        this.random = Objects.requireNonNull(random, "random is required");
        if (minIntervalTicks < 1) {
            throw new IllegalArgumentException("minIntervalTicks must be >= 1");
        }
        if (maxIntervalTicks < minIntervalTicks) {
            throw new IllegalArgumentException("maxIntervalTicks must be >= minIntervalTicks");
        }
        if (windowTicks < 1) {
            throw new IllegalArgumentException("windowTicks must be >= 1");
        }
        this.minIntervalTicks = minIntervalTicks;
        this.maxIntervalTicks = maxIntervalTicks;
        this.windowTicks = windowTicks;
        this.ticksUntilNextEvent = rollInterval();
    }

    /**
     * Advances the scheduler by one tick: manages the open event (kill/timeout) or, when none is
     * open, counts down to and fires the next event. Must only be called on the tick thread
     * (AGENTS.md §5).
     */
    @Override
    public void tick() {
        if (activeMob != null) {
            manageActiveEvent();
            return;
        }
        ticksUntilNextEvent--;
        if (ticksUntilNextEvent > 0) {
            return;
        }
        startEvent();
    }

    /**
     * Resolves the currently open event: if its mob has been slain the slain instance is cleared
     * (its death was already announced by the world-boss kill path); otherwise the window is
     * decremented and, on expiry, the mob fades away unkilled with a server-wide notice.
     */
    private void manageActiveEvent() {
        MobInstance mob = Objects.requireNonNull(activeMob);
        if (!mob.isAlive()) {
            stage.purgeInstance(mob);
            log.debug("World-event mob {} slain; closing event", mob.template().name());
            closeEvent();
            return;
        }
        windowRemaining--;
        if (windowRemaining <= 0) {
            announcer.announceEventTimeout(mob.template().name(), mob.roomId());
            stage.purgeInstance(mob);
            log.debug("World-event mob {} despawned unkilled after {} ticks",
                mob.template().name(), windowTicks);
            closeEvent();
        }
    }

    /**
     * Opens a fresh world event by spawning a randomly chosen world-event mob and announcing it. When
     * no world-event template is defined, or the spawn fails, no event opens and the next interval is
     * rolled so the scheduler tries again later.
     */
    private void startEvent() {
        List<MobTemplate> templates = stage.worldEventTemplates();
        if (templates.isEmpty()) {
            ticksUntilNextEvent = rollInterval();
            return;
        }
        MobTemplate chosen = templates.get(random.roll(0, templates.size() - 1));
        Optional<MobInstance> spawned = stage.spawnInstance(chosen.id(), chosen.spawnRoomId());
        if (spawned.isEmpty()) {
            log.warn("World-event template {} could not be spawned; skipping event", chosen.id().getValue());
            ticksUntilNextEvent = rollInterval();
            return;
        }
        activeMob = spawned.get();
        windowRemaining = windowTicks;
        announcer.announceEventSpawn(chosen.name(), chosen.spawnRoomId());
        log.debug("Opened world event: {} in {} for up to {} ticks",
            chosen.name(), chosen.spawnRoomId(), windowTicks);
    }

    private void closeEvent() {
        activeMob = null;
        windowRemaining = 0;
        ticksUntilNextEvent = rollInterval();
    }

    private int rollInterval() {
        return minIntervalTicks + random.roll(0, maxIntervalTicks - minIntervalTicks);
    }

    /**
     * Whether a world event is currently open (a rare-elite mob is live in the world); for tests.
     *
     * @return {@code true} when an event is active
     */
    public boolean isEventActive() {
        return activeMob != null;
    }
}
