package io.taanielo.jmud.core.transport.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a single ferry definition file ({@code data/ferries/<ferry-id>.json}).
 *
 * @param schemaVersion     the file schema version
 * @param id                the ferry id
 * @param name              the human-readable display name
 * @param deckRoomId        the room players stand in while aboard
 * @param route             the ordered dock room ids the ferry visits
 * @param ticksPerLeg       ticks the ferry waits at each dock before departing
 * @param startLegIndex     the index into {@code route} the ferry starts docked at (defaults to 0)
 * @param departureMessages optional flavour lines announced on departure
 * @param arrivalMessages   optional flavour lines announced on arrival
 */
record FerryDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String name,
    @Nullable String deckRoomId,
    @Nullable List<String> route,
    int ticksPerLeg,
    @Nullable Integer startLegIndex,
    @Nullable List<String> departureMessages,
    @Nullable List<String> arrivalMessages
) {
}
