package io.taanielo.jmud.core.transport;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Immutable definition of a scheduled ferry that carries players between docks.
 *
 * <p>A ferry is a "transport room": players board by moving into its {@link #deckRoomId() deck
 * room}, and every {@link #ticksPerLeg()} ticks the {@link BoatEngine} advances the ferry to the
 * next dock on its {@link #route() route} (cycling back to the first after the last) and carries
 * everyone on the deck to that dock. Because the schedule is expressed purely in ticks, the arrival
 * sequence is deterministic (AGENTS.md §5).
 *
 * <p>Optional {@link #departureMessages() departure} and {@link #arrivalMessages() arrival} flavour
 * pools are chosen from through the {@link io.taanielo.jmud.core.combat.CombatRandom} port; when a
 * pool is empty a sensible default line is used instead.
 *
 * @param id                the stable ferry id
 * @param name              the human-readable display name (used in default announcements)
 * @param deckRoomId        the room players stand in while aboard the ferry
 * @param route             the ordered list of dock room ids the ferry visits (at least two)
 * @param ticksPerLeg       the number of ticks the ferry waits at each dock before departing
 * @param startLegIndex     the index into {@link #route()} the ferry starts docked at
 * @param departureMessages optional flavour lines announced when the ferry departs (may be empty)
 * @param arrivalMessages   optional flavour lines announced when the ferry arrives (may be empty)
 */
public record Ferry(
    FerryId id,
    String name,
    RoomId deckRoomId,
    List<RoomId> route,
    int ticksPerLeg,
    int startLegIndex,
    List<String> departureMessages,
    List<String> arrivalMessages
) {

    /**
     * Validates and defensively copies the ferry definition.
     */
    public Ferry {
        Objects.requireNonNull(id, "Ferry id is required");
        Objects.requireNonNull(name, "Ferry name is required");
        Objects.requireNonNull(deckRoomId, "Ferry deck room id is required");
        Objects.requireNonNull(route, "Ferry route is required");
        Objects.requireNonNull(departureMessages, "Ferry departure messages are required (use an empty list)");
        Objects.requireNonNull(arrivalMessages, "Ferry arrival messages are required (use an empty list)");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Ferry name must not be blank");
        }
        route = List.copyOf(route);
        if (route.size() < 2) {
            throw new IllegalArgumentException("Ferry route must contain at least two docks");
        }
        if (ticksPerLeg <= 0) {
            throw new IllegalArgumentException("Ferry ticks per leg must be positive");
        }
        if (startLegIndex < 0 || startLegIndex >= route.size()) {
            throw new IllegalArgumentException(
                "Ferry start leg index " + startLegIndex + " is out of range for route of size " + route.size());
        }
        departureMessages = List.copyOf(departureMessages);
        arrivalMessages = List.copyOf(arrivalMessages);
    }

    /**
     * Returns the dock room id at the given leg index.
     *
     * @param legIndex a valid index into {@link #route()}
     * @return the dock room id at that leg
     */
    public RoomId dockAt(int legIndex) {
        return route.get(legIndex);
    }

    /**
     * Returns the leg index following the given one, wrapping around to the first dock after the
     * last so the ferry runs its route as an endless loop.
     *
     * @param legIndex the current leg index
     * @return the next leg index (cyclic)
     */
    public int nextLegIndex(int legIndex) {
        return (legIndex + 1) % route.size();
    }
}
