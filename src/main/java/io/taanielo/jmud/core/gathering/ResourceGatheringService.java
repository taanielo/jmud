package io.taanielo.jmud.core.gathering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Application service for resource gathering: harvesting raw crafting materials from the resource
 * nodes placed around the world and tracking their depletion/respawn cycle.
 *
 * <p>The service owns the transient depletion state keyed by {@link ResourceNodeId} — a countdown of
 * ticks remaining until a harvested node respawns. This state is never persisted (the same tradeoff
 * mob respawns make) and is mutated only on the tick thread (AGENTS.md §5): harvest attempts arrive
 * through the per-player command queue and respawn countdowns are driven by
 * {@link ResourceNodeRespawnTicker}. Because command execution is serialised on the single tick
 * thread, two players racing for the same node resolve deterministically: the first
 * {@link #gather} call depletes the node and the second sees it as already stripped.
 *
 * <p>The {@link Player} passed in is never mutated; on success the caller receives an updated copy in
 * the returned {@link GatherOutcome}, which it applies on the tick thread. Node definitions are
 * immutable, so a single instance is safe to share across sessions.
 */
public class ResourceGatheringService {

    private final Map<RoomId, List<ResourceNode>> nodesByRoom;
    private final ItemRepository itemRepository;
    private final ConcurrentHashMap<ResourceNodeId, Integer> respawnCountdown = new ConcurrentHashMap<>();

    /**
     * Creates a gathering service over a fixed set of resource nodes.
     *
     * @param nodes          the configured resource nodes; copied defensively, may be empty
     * @param itemRepository repository used to resolve a node's yield item definition
     */
    public ResourceGatheringService(List<ResourceNode> nodes, ItemRepository itemRepository) {
        Objects.requireNonNull(nodes, "Nodes are required");
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        Map<RoomId, List<ResourceNode>> byRoom = new HashMap<>();
        for (ResourceNode node : nodes) {
            byRoom.computeIfAbsent(node.roomId(), id -> new ArrayList<>()).add(node);
        }
        Map<RoomId, List<ResourceNode>> immutable = new HashMap<>();
        byRoom.forEach((room, list) -> immutable.put(room, List.copyOf(list)));
        this.nodesByRoom = Map.copyOf(immutable);
    }

    /**
     * Attempts to harvest an available resource node in the given room on behalf of the player.
     *
     * <p>If no node exists in the room the attempt fails with a "nothing here" message; if every node
     * present has already been harvested it fails with an "already stripped" message. On success the
     * node's yield item is added to the player's inventory, the node is marked depleted for its
     * configured respawn delay, and the returned outcome carries the updated player.
     *
     * @param player the harvesting player
     * @param roomId the room the player is standing in
     * @return the outcome describing success or the reason for failure
     */
    public GatherOutcome gather(Player player, RoomId roomId) {
        Objects.requireNonNull(player, "Player is required");
        Objects.requireNonNull(roomId, "Room id is required");
        List<ResourceNode> inRoom = nodesByRoom.getOrDefault(roomId, List.of());
        if (inRoom.isEmpty()) {
            return GatherOutcome.failure("There is nothing here to gather.");
        }
        Optional<ResourceNode> available = inRoom.stream()
            .filter(node -> !isDepleted(node.id()))
            .findFirst();
        if (available.isEmpty()) {
            ResourceNode depleted = inRoom.getFirst();
            return GatherOutcome.failure(
                "The " + depleted.name() + " has already been stripped bare. Come back later.");
        }
        ResourceNode node = available.get();

        Item yield;
        try {
            Optional<Item> resolved = itemRepository.findById(node.yieldItemId());
            if (resolved.isEmpty()) {
                return GatherOutcome.failure(
                    "You work at the " + node.name() + " but come away empty-handed.");
            }
            yield = resolved.get();
        } catch (RepositoryException e) {
            return GatherOutcome.failure(
                "You work at the " + node.name() + " but come away empty-handed.");
        }

        respawnCountdown.put(node.id(), node.respawnTicks());
        Player updated = player.addItem(yield);
        return GatherOutcome.success(
            "You work the " + node.name() + " and collect " + yield.getName() + ".", updated);
    }

    /**
     * Returns the look-description lines for every node currently available in the given room.
     *
     * <p>Depleted nodes are omitted so the room description reflects only what can be harvested right
     * now. Called from the {@code LOOK} handler on the tick thread.
     *
     * @param roomId the room to describe
     * @return an ordered list of node description lines, empty if no node is available
     */
    public List<String> describeAvailableNodes(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        List<ResourceNode> inRoom = nodesByRoom.getOrDefault(roomId, List.of());
        List<String> lines = new ArrayList<>();
        for (ResourceNode node : inRoom) {
            if (!isDepleted(node.id())) {
                lines.add(node.lookDescription());
            }
        }
        return List.copyOf(lines);
    }

    /**
     * Advances every depleted node's respawn countdown by one tick, making nodes available again once
     * their countdown elapses. Invoked once per tick by {@link ResourceNodeRespawnTicker}.
     */
    public void tickRespawns() {
        for (ResourceNodeId id : List.copyOf(respawnCountdown.keySet())) {
            respawnCountdown.compute(id, (key, remaining) ->
                remaining == null || remaining <= 1 ? null : remaining - 1);
        }
    }

    /**
     * Returns whether the node with the given id is currently depleted (harvested and awaiting
     * respawn). Package-private; primarily exposed for tests.
     *
     * @param id the node id to query
     * @return {@code true} if the node is depleted, {@code false} if available
     */
    boolean isDepleted(ResourceNodeId id) {
        Integer remaining = respawnCountdown.get(id);
        return remaining != null && remaining > 0;
    }
}
