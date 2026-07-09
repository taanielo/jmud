package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.RoomId;

/**
 * World-level {@link Tickable} that runs the Arena: on a fixed tick interval it selects two random
 * online players and challenges them to a consensual duel through the existing {@link DuelService},
 * announcing the event to every online player and to spectators standing in the arena rooms.
 *
 * <p>The ticker is pure in-memory state (a countdown and a small list of in-progress arena duels)
 * and mutates only on the tick thread, so it introduces no blocking I/O and no concurrency of its
 * own (AGENTS.md §5). Player selection is drawn from the shared {@link CombatRandom} port so the
 * schedule is reproducible from a world seed rather than depending on a bare {@code Random}.
 *
 * <p>Announcement lifecycle for a single arena duel:
 * <ol>
 *   <li><em>Challenge fired</em>: a global broadcast names both combatants and the arena location;
 *       the challenged player is prompted to {@code ACCEPT}.</li>
 *   <li><em>Duel begins</em>: once the target accepts and the duel becomes active, spectators in the
 *       arena rooms are told the fight has begun.</li>
 *   <li><em>Duel ends</em>: when the active duel resolves, spectators are told it is over.</li>
 * </ol>
 * A challenge that is never accepted (times out or the players disconnect) is quietly dropped.
 */
public class ArenaEventTicker implements Tickable {

    /** Ticks between arena duel events (~75 seconds at the default 1s/tick). */
    public static final int ARENA_DUEL_INTERVAL_TICKS = 75;

    /** Maximum number of arena duels tracked concurrently; further events are skipped until one ends. */
    public static final int ARENA_DUEL_POOL_SIZE = 1;

    /** The arena's public entrance room. */
    public static final RoomId ARENA_ENTRANCE = RoomId.of("arena-entrance");

    /** The fighting pit where announced duels are fought. */
    public static final RoomId ARENA_PIT = RoomId.of("arena-pit");

    /** The spectator stands overlooking the pit. */
    public static final RoomId ARENA_STANDS = RoomId.of("arena-stands");

    private static final List<RoomId> ARENA_ROOMS = List.of(ARENA_ENTRANCE, ARENA_PIT, ARENA_STANDS);

    private final DuelService duelService;
    private final MessageBroadcaster messageBroadcaster;
    private final OnlinePlayersSupplier onlinePlayers;
    private final CombatRandom random;
    private final int intervalTicks;
    private final int poolSize;

    private final List<ArenaDuel> arenaDuels = new ArrayList<>();
    private int ticksUntilNextEvent;

    /**
     * Creates an arena ticker with the default interval and pool size.
     *
     * @param duelService        the transient duel registry used to issue and track challenges
     * @param messageBroadcaster the sanctioned fan-out used for global and room-scoped announcements
     * @param onlinePlayers      supplies the pool of online players eligible to be drafted
     * @param random             the shared RNG port used to pick combatants deterministically
     */
    public ArenaEventTicker(
        DuelService duelService,
        MessageBroadcaster messageBroadcaster,
        OnlinePlayersSupplier onlinePlayers,
        CombatRandom random
    ) {
        this(duelService, messageBroadcaster, onlinePlayers, random,
            ARENA_DUEL_INTERVAL_TICKS, ARENA_DUEL_POOL_SIZE);
    }

    /**
     * Creates an arena ticker with an explicit interval and pool size, primarily for tests.
     *
     * @param duelService        the transient duel registry used to issue and track challenges
     * @param messageBroadcaster the sanctioned fan-out used for global and room-scoped announcements
     * @param onlinePlayers      supplies the pool of online players eligible to be drafted
     * @param random             the shared RNG port used to pick combatants deterministically
     * @param intervalTicks      number of ticks between arena duel events (must be positive)
     * @param poolSize           maximum number of concurrent arena duels (must be positive)
     */
    public ArenaEventTicker(
        DuelService duelService,
        MessageBroadcaster messageBroadcaster,
        OnlinePlayersSupplier onlinePlayers,
        CombatRandom random,
        int intervalTicks,
        int poolSize
    ) {
        this.duelService = Objects.requireNonNull(duelService, "duelService is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "messageBroadcaster is required");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers is required");
        this.random = Objects.requireNonNull(random, "random is required");
        if (intervalTicks <= 0) {
            throw new IllegalArgumentException("intervalTicks must be positive");
        }
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive");
        }
        this.intervalTicks = intervalTicks;
        this.poolSize = poolSize;
        this.ticksUntilNextEvent = intervalTicks;
    }

    /**
     * Advances the arena by one tick: reports on any in-progress duels, then, when the countdown
     * elapses, fires a fresh duel event. Must only be called on the tick thread (AGENTS.md §5).
     */
    @Override
    public void tick() {
        updateInProgressDuels();
        ticksUntilNextEvent--;
        if (ticksUntilNextEvent > 0) {
            return;
        }
        ticksUntilNextEvent = intervalTicks;
        fireEvent();
    }

    /**
     * Detects arena duels that have started (target accepted) or ended (duel resolved) since the
     * previous tick and notifies spectators accordingly.
     */
    private void updateInProgressDuels() {
        Iterator<ArenaDuel> iterator = arenaDuels.iterator();
        while (iterator.hasNext()) {
            ArenaDuel duel = iterator.next();
            boolean active = duelService.areDueling(duel.challenger(), duel.target());
            if (duel.engaged()) {
                if (!active) {
                    announceToArena(duel.challenger().getValue() + " and " + duel.target().getValue()
                        + "'s duel in the Arena has ended.");
                    iterator.remove();
                }
                continue;
            }
            if (active) {
                duel.markEngaged();
                announceToArena("The duel between " + duel.challenger().getValue() + " and "
                    + duel.target().getValue() + " begins in the Arena pit!");
            } else if (duelService.pendingChallenger(duel.target()).isEmpty()) {
                // The challenge lapsed or was declined before it ever became an active duel.
                iterator.remove();
            }
        }
    }

    /**
     * Selects two eligible players at random and issues an arena duel challenge between them,
     * skipping silently when there are too few players or the concurrent-duel pool is full.
     */
    private void fireEvent() {
        if (arenaDuels.size() >= poolSize) {
            return;
        }
        List<Username> eligible = new ArrayList<>();
        for (Username username : onlinePlayers.onlineUsernames()) {
            if (!duelService.isDueling(username)
                && duelService.pendingChallenger(username).isEmpty()) {
                eligible.add(username);
            }
        }
        if (eligible.size() < 2) {
            return;
        }
        int first = random.roll(0, eligible.size() - 1);
        int second = random.roll(0, eligible.size() - 2);
        if (second >= first) {
            second++;
        }
        Username challenger = eligible.get(first);
        Username target = eligible.get(second);

        duelService.requestDuel(challenger, target);
        arenaDuels.add(new ArenaDuel(challenger, target));

        messageBroadcaster.broadcastGlobal(new PlainTextMessage(
            "[Arena] " + challenger.getValue() + " and " + target.getValue()
                + " have been summoned to duel in the Arena! " + target.getValue()
                + ", type ACCEPT to fight. Travel to the arena to spectate."),
            Set.of());
        messageBroadcaster.sendToPlayer(challenger, new PlainTextMessage(
            "The Arena has chosen you to duel " + target.getValue()
                + ". Await their ACCEPT."));
        messageBroadcaster.sendToPlayer(target, new PlainTextMessage(
            "The Arena has pitted you against " + challenger.getValue()
                + ". Type ACCEPT to engage or wait for the challenge to lapse."));
        announceToArena("A duel between " + challenger.getValue() + " and " + target.getValue()
            + " has been announced!");
    }

    /**
     * Delivers a room-scoped message to every occupant of every arena room.
     *
     * @param text the announcement to deliver to arena spectators
     */
    private void announceToArena(String text) {
        PlainTextMessage message = new PlainTextMessage(text);
        for (RoomId room : ARENA_ROOMS) {
            messageBroadcaster.broadcastToRoom(room, message, Set.of());
        }
    }

    /**
     * Number of arena duels currently being tracked (announced but not yet ended); for tests.
     *
     * @return the count of in-progress arena duels
     */
    public int trackedDuelCount() {
        return arenaDuels.size();
    }

    /** A single arena duel, tracked from announcement until the active fight resolves. */
    private static final class ArenaDuel {
        private final Username challenger;
        private final Username target;
        private boolean engaged;

        private ArenaDuel(Username challenger, Username target) {
            this.challenger = challenger;
            this.target = target;
            this.engaged = false;
        }

        private Username challenger() {
            return challenger;
        }

        private Username target() {
            return target;
        }

        private boolean engaged() {
            return engaged;
        }

        private void markEngaged() {
            this.engaged = true;
        }
    }
}
