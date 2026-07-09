package io.taanielo.jmud.core.transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.PlayerLocationService;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Tick-driven scheduler that sails every configured {@link Ferry} along its route.
 *
 * <p>On each tick (AGENTS.md §5) the engine decrements every ferry's departure countdown. When a
 * ferry's countdown reaches zero it departs: the players standing in its deck room are carried to
 * the next dock on its {@link Ferry#route() route} (cycling back to the first after the last), and
 * the countdown is reset to a full leg. Departure notices go to the deck occupants and the dock
 * being left; an arrival notice goes to the destination dock. All delivery flows through the
 * sanctioned {@link MessageBroadcaster} (AGENTS.md §3.3).
 *
 * <p>Because departures are driven purely by tick counts, the arrival sequence is deterministic and
 * replayable; any flavour-line choice is drawn from the {@link CombatRandom} port rather than a bare
 * {@code Random}. Multiple ferries run independently: each has its own entry in the
 * tick-thread-confined {@link #states} map, so their schedules never interfere.
 *
 * <p>All mutable state ({@link #states}) is confined to the tick thread; nothing here is read from
 * connection threads and no synchronization is required.
 */
public class BoatEngine implements Tickable {

    private final CombatRandom random;
    private final PlayerLocationService playerLocationService;
    private final MessageBroadcaster messageBroadcaster;
    private final List<Ferry> ferries;

    // Tick-thread-only per-ferry schedule state, iterated in insertion (definition) order so the
    // arrival sequence is reproducible across runs.
    private final Map<FerryId, FerryState> states = new LinkedHashMap<>();

    /**
     * Creates a boat engine for the given ferries.
     *
     * @param random                the RNG port used to pick flavour lines (no bare {@code Random};
     *                              AGENTS.md §5)
     * @param playerLocationService service used to find deck occupants and relocate them on arrival
     * @param messageBroadcaster    the sanctioned fan-out used to notify docks and passengers
     * @param ferries               the ferry definitions to schedule (may be empty)
     */
    public BoatEngine(
        CombatRandom random,
        PlayerLocationService playerLocationService,
        MessageBroadcaster messageBroadcaster,
        List<Ferry> ferries
    ) {
        this.random = Objects.requireNonNull(random, "Combat random is required");
        this.playerLocationService =
            Objects.requireNonNull(playerLocationService, "Player location service is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
        this.ferries = List.copyOf(Objects.requireNonNull(ferries, "Ferries are required"));
        for (Ferry ferry : this.ferries) {
            states.put(ferry.id(), FerryState.initial(ferry));
        }
    }

    /**
     * Advances every ferry by one tick, departing any whose countdown has elapsed. Must only be
     * called from the tick thread.
     */
    @Override
    public void tick() {
        for (Ferry ferry : ferries) {
            // Every ferry is seeded with state in the constructor, so this is never null.
            FerryState state = Objects.requireNonNull(states.get(ferry.id()));
            int remaining = state.countdown() - 1;
            if (remaining > 0) {
                states.put(ferry.id(), new FerryState(state.currentLegIndex(), remaining));
                continue;
            }
            int nextLeg = ferry.nextLegIndex(state.currentLegIndex());
            depart(ferry, state.currentLegIndex(), nextLeg);
            states.put(ferry.id(), new FerryState(nextLeg, ferry.ticksPerLeg()));
        }
    }

    /**
     * Returns the dock room id a ferry is currently moored at (for tests and status queries).
     *
     * @param ferryId the ferry to query
     * @return the current dock room id, or {@code null} if the ferry is unknown
     */
    public @Nullable RoomId currentDock(FerryId ferryId) {
        FerryState state = states.get(ferryId);
        if (state == null) {
            return null;
        }
        Ferry ferry = ferryById(ferryId);
        return ferry == null ? null : ferry.dockAt(state.currentLegIndex());
    }

    private void depart(Ferry ferry, int fromLeg, int toLeg) {
        RoomId origin = ferry.dockAt(fromLeg);
        RoomId destination = ferry.dockAt(toLeg);
        RoomId deck = ferry.deckRoomId();
        List<Username> passengers = new ArrayList<>(playerLocationService.getPlayersInRoom(deck));

        String departureLine = departureLine(ferry);
        messageBroadcaster.broadcastToRoom(deck, new PlainTextMessage(departureLine), Set.of());
        messageBroadcaster.broadcastToRoom(origin, new PlainTextMessage(departureLine), Set.of());

        for (Username passenger : passengers) {
            playerLocationService.movePlayerTo(passenger, destination);
        }

        String arrivalLine = arrivalLine(ferry);
        messageBroadcaster.broadcastToRoom(destination, new PlainTextMessage(arrivalLine), Set.of());
    }

    private String departureLine(Ferry ferry) {
        List<String> pool = ferry.departureMessages();
        if (pool.isEmpty()) {
            return "The " + ferry.name() + " casts off and sails away.";
        }
        return pool.get(random.roll(0, pool.size() - 1));
    }

    private String arrivalLine(Ferry ferry) {
        List<String> pool = ferry.arrivalMessages();
        if (pool.isEmpty()) {
            return "The " + ferry.name() + " glides up to the dock and moors.";
        }
        return pool.get(random.roll(0, pool.size() - 1));
    }

    private @Nullable Ferry ferryById(FerryId ferryId) {
        for (Ferry ferry : ferries) {
            if (ferry.id().equals(ferryId)) {
                return ferry;
            }
        }
        return null;
    }
}
