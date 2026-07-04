package io.taanielo.jmud.core.messaging;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Default {@link MessageBroadcaster}: resolves recipients via the {@link ClientPool} (for the
 * connected-clients snapshot) and {@link RoomService} (for room occupancy), and never depends on
 * any concrete transport type. Constructed only by the composition root (AGENTS.md §3.3).
 *
 * <p>Safe to call from both reader threads (e.g. during login) and the tick thread: the backing
 * {@link ClientPool#clients()} snapshot is an immutable {@link java.util.List} copy, so no locking
 * is required here (AGENTS.md §5).
 */
public class MessageBroadcasterImpl implements MessageBroadcaster {

    private final ClientPool clientPool;
    private final RoomService roomService;

    /**
     * Creates a broadcaster backed by the given client pool and room service.
     *
     * @param clientPool  provides the current snapshot of connected clients
     * @param roomService  provides room occupancy lookups
     */
    public MessageBroadcasterImpl(ClientPool clientPool, RoomService roomService) {
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
    }

    @Override
    public void sendToPlayer(Username target, Message message) {
        Objects.requireNonNull(target, "Target username is required");
        Objects.requireNonNull(message, "Message is required");
        findClient(target).ifPresent(client -> client.sendMessage(message));
    }

    @Override
    public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        Objects.requireNonNull(room, "Room id is required");
        Objects.requireNonNull(message, "Message is required");
        Set<Username> excluded = exclude == null ? Set.of() : exclude;
        List<Username> occupants = roomService.getPlayersInRoom(room);
        for (Username occupant : occupants) {
            if (excluded.contains(occupant)) {
                continue;
            }
            findClient(occupant).ifPresent(client -> client.sendMessage(message));
        }
    }

    @Override
    public void broadcastGlobal(Message message, Set<Username> exclude) {
        Objects.requireNonNull(message, "Message is required");
        Set<Username> excluded = exclude == null ? Set.of() : exclude;
        for (Client client : clientPool.clients()) {
            client.currentPlayer()
                .map(Player::getUsername)
                .filter(username -> !excluded.contains(username))
                .ifPresent(ignored -> client.sendMessage(message));
        }
    }

    private Optional<Client> findClient(Username username) {
        return clientPool.clients().stream()
            .filter(client -> client.currentPlayer()
                .map(player -> player.getUsername().equals(username))
                .orElse(false))
            .findFirst();
    }
}
