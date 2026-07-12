package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.audit.AuditEntry;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSink;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies the {@link ShutdownCoordinator} runs its steps in the mandated
 * order (stop accepting -> notify -> stop ticks -> save players -> flush
 * audit -> clear tick registry) and that one player's failed save does not
 * prevent the others from being attempted (issue #170).
 */
class ShutdownCoordinatorTest {

    @Test
    void runsStepsInMandatedOrder() {
        List<String> events = new ArrayList<>();

        Server server = new RecordingServer(events);
        RecordingClient onlineClient = new RecordingClient(events, "ana", true);
        RecordingClient anonymousClient = new RecordingClient(events, null, false);
        ClientPool clientPool = new FixedClientPool(List.of(onlineClient, anonymousClient));

        TickRegistry tickRegistry = new TickRegistry() {
            @Override
            public void clear() {
                events.add("tickRegistry.clear");
                super.clear();
            }
        };
        FixedRateTickScheduler tickScheduler = new FixedRateTickScheduler(
            tickRegistry, 50, Executors.newSingleThreadScheduledExecutor()
        ) {
            @Override
            public void stop() {
                events.add("tickScheduler.stop");
                super.stop();
            }
        };
        tickScheduler.start();

        RecordingPlayerRepository playerRepository = new RecordingPlayerRepository(events);

        AuditService auditService = new AuditService(new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        }, java.time.Clock.systemUTC(), () -> 0L, () -> "corr") {
            @Override
            public void shutdown(Duration timeout) {
                events.add("auditService.shutdown");
                super.shutdown(timeout);
            }
        };
        PersistenceQueue persistenceQueue = new PersistenceQueue(playerRepository, auditService);

        ShutdownCoordinator coordinator = new ShutdownCoordinator(
            List.of(server), clientPool, tickScheduler, tickRegistry, persistenceQueue, auditService, Duration.ofMillis(200)
        );

        coordinator.shutdown();

        assertEquals(
            List.of(
                "server.stop",
                "notify:ana",
                "notify:anonymous",
                "tickScheduler.stop",
                "save:ana",
                "auditService.shutdown",
                "tickRegistry.clear"
            ),
            events
        );
    }

    @Test
    void onePlayersFailedSaveDoesNotPreventOthersFromSaving() {
        List<String> events = new ArrayList<>();

        RecordingClient failing = new RecordingClient(events, "grumpy", true);
        RecordingClient healthy = new RecordingClient(events, "happy", true);
        ClientPool clientPool = new FixedClientPool(List.of(failing, healthy));

        TickRegistry tickRegistry = new TickRegistry();
        FixedRateTickScheduler tickScheduler = new FixedRateTickScheduler(
            tickRegistry, 50, Executors.newSingleThreadScheduledExecutor()
        );
        tickScheduler.start();

        PlayerRepository playerRepository = new PlayerRepository() {
            @Override
            public void savePlayer(Player player) throws RepositoryException {
                events.add("save:" + player.getUsername().getValue());
                if (player.getUsername().getValue().equals("grumpy")) {
                    throw new RepositoryException("disk full (simulated)");
                }
            }

            @Override
            public Optional<Player> loadPlayer(Username username) {
                return Optional.empty();
            }
        };

        AuditService auditService = new AuditService(new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        }, java.time.Clock.systemUTC(), () -> 0L, () -> "corr");
        PersistenceQueue persistenceQueue = new PersistenceQueue(playerRepository, auditService);

        ShutdownCoordinator coordinator = new ShutdownCoordinator(
            List.of(), clientPool, tickScheduler, tickRegistry, persistenceQueue, auditService, Duration.ofMillis(200)
        );

        coordinator.shutdown();

        assertTrue(events.contains("save:grumpy"), "Failing save must still be attempted");
        assertTrue(events.contains("save:happy"), "Other players must still be saved despite one failure");
    }

    private static Player newPlayer(String username) {
        User user = User.of(Username.of(username), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private static final class RecordingServer implements Server {
        private final List<String> events;

        private RecordingServer(List<String> events) {
            this.events = events;
        }

        @Override
        public void run() {
        }

        @Override
        public void stop() {
            events.add("server.stop");
        }
    }

    private static final class RecordingClient implements Client {
        private final List<String> events;
        private final Optional<Player> player;

        private RecordingClient(List<String> events, String username, boolean online) {
            this.events = events;
            this.player = online ? Optional.of(newPlayer(username)) : Optional.empty();
        }

        @Override
        public void run() {
        }

        @Override
        public void sendMessage(Message message) {
            player.ifPresentOrElse(
                p -> events.add("notify:" + p.getUsername().getValue()),
                () -> events.add("notify:anonymous")
            );
        }

        @Override
        public void close() {
        }

        @Override
        public Optional<Player> currentPlayer() {
            return player;
        }
    }

    private static final class RecordingPlayerRepository implements PlayerRepository {
        private final List<String> events;

        private RecordingPlayerRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public void savePlayer(Player player) throws RepositoryException {
            events.add("save:" + player.getUsername().getValue());
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.empty();
        }
    }

    private static final class FixedClientPool implements ClientPool {
        private final List<Client> clients;

        private FixedClientPool(List<Client> clients) {
            this.clients = clients;
        }

        @Override
        public void add(Client client) {
        }

        @Override
        public void remove(Client client) {
        }

        @Override
        public void promoteToWorld(Client client) {
        }

        @Override
        public int getNextId() {
            return 0;
        }

        @Override
        public List<Client> allConnections() {
            return clients;
        }

        // Every fixture client is treated as already in-world.
        @Override
        public List<Client> inWorld() {
            return clients;
        }
    }
}
