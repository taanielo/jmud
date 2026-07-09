package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Tick-driven source of ambient room flavour (dripping water, distant howls, creaking boards).
 *
 * <p>Every tick the engine runs on the tick thread (AGENTS.md §5) and, for each currently occupied
 * room that declares an {@link Room#getAmbientMessages() ambient message pool}, counts down an
 * independent per-room timer. When a room's timer expires the engine picks one of its messages and
 * delivers it to everyone in the room through the sanctioned {@link MessageBroadcaster}
 * (AGENTS.md §3.3), then re-rolls the timer to a fresh interval in {@code [minInterval, maxInterval]}
 * ticks so emissions stay occasional and non-intrusive.
 *
 * <p>Both the timers and the message choice are drawn from the {@link CombatRandom} RNG port so the
 * whole sequence is deterministic and replayable alongside combat and weather rolls; no bare
 * {@code Random} is used. Empty and silent rooms are skipped entirely, and per-room timers for rooms
 * that empty out are dropped so the bookkeeping map never grows unbounded.
 *
 * <p>All state ({@link #countdowns}) is confined to the tick thread; nothing here is read from
 * connection threads.
 */
public class AmbientMessageEngine implements Tickable {

    private final CombatRandom random;
    private final RoomRepository roomRepository;
    private final PlayerLocationService playerLocationService;
    private final MessageBroadcaster messageBroadcaster;
    private final int minIntervalTicks;
    private final int maxIntervalTicks;

    // Tick-thread-only per-room countdown timers (ticks until the next emission).
    private final Map<RoomId, Integer> countdowns = new HashMap<>();

    /**
     * Creates an ambient message engine.
     *
     * @param random                the RNG port used to schedule and choose emissions (no bare
     *                              {@code Random}; AGENTS.md §5)
     * @param roomRepository        repository used to resolve a room's ambient message pool
     * @param playerLocationService source of which rooms currently contain players
     * @param messageBroadcaster    the sanctioned fan-out used to deliver messages to a room
     * @param minIntervalTicks      minimum ticks between emissions in a room; must be positive
     * @param maxIntervalTicks      maximum ticks between emissions in a room; must be {@code >= min}
     */
    public AmbientMessageEngine(
        CombatRandom random,
        RoomRepository roomRepository,
        PlayerLocationService playerLocationService,
        MessageBroadcaster messageBroadcaster,
        int minIntervalTicks,
        int maxIntervalTicks
    ) {
        this.random = Objects.requireNonNull(random, "Combat random is required");
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
        this.playerLocationService =
            Objects.requireNonNull(playerLocationService, "Player location service is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
        if (minIntervalTicks <= 0) {
            throw new IllegalArgumentException("Minimum interval must be positive");
        }
        if (maxIntervalTicks < minIntervalTicks) {
            throw new IllegalArgumentException("Maximum interval must be >= minimum interval");
        }
        this.minIntervalTicks = minIntervalTicks;
        this.maxIntervalTicks = maxIntervalTicks;
    }

    /**
     * Advances every occupied room's ambient timer by one tick, emitting a flavour line to any room
     * whose timer has expired. Must only be called from the tick thread.
     */
    @Override
    public void tick() {
        List<Room> eligible = eligibleRooms();
        Set<RoomId> eligibleIds = new HashSet<>();
        for (Room room : eligible) {
            eligibleIds.add(room.getId());
        }
        countdowns.keySet().retainAll(eligibleIds);
        for (Room room : eligible) {
            Integer remaining = countdowns.get(room.getId());
            if (remaining == null) {
                countdowns.put(room.getId(), rollInterval());
                continue;
            }
            int next = remaining - 1;
            if (next > 0) {
                countdowns.put(room.getId(), next);
                continue;
            }
            emit(room);
            countdowns.put(room.getId(), rollInterval());
        }
    }

    /**
     * Returns the currently occupied rooms that have an ambient message pool, in a deterministic
     * order (by room id) so emission scheduling is reproducible.
     */
    private List<Room> eligibleRooms() {
        List<RoomId> occupied = new ArrayList<>(playerLocationService.occupiedRooms());
        occupied.sort((a, b) -> a.getValue().compareTo(b.getValue()));
        List<Room> eligible = new ArrayList<>();
        for (RoomId roomId : occupied) {
            Room room = loadRoom(roomId);
            if (room != null && room.hasAmbientMessages()) {
                eligible.add(room);
            }
        }
        return eligible;
    }

    private void emit(Room room) {
        List<String> messages = room.getAmbientMessages();
        String message = messages.get(random.roll(0, messages.size() - 1));
        messageBroadcaster.broadcastToRoom(room.getId(), new PlainTextMessage(message), Set.of());
    }

    private int rollInterval() {
        return random.roll(minIntervalTicks, maxIntervalTicks);
    }

    private Room loadRoom(RoomId roomId) {
        try {
            return roomRepository.findById(roomId).orElse(null);
        } catch (RepositoryException e) {
            return null;
        }
    }
}
