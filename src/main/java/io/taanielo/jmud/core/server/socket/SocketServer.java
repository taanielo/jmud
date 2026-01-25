package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.UserRegistryImpl;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.repository.AbilityRepositoryException;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageBroadcasterImpl;
import io.taanielo.jmud.core.player.JsonPlayerRepository;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickClock;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.InMemoryRoomRepository;
import io.taanielo.jmud.core.world.repository.RoomRepository;

@Slf4j
public class SocketServer implements Server {
    private final int port;

    private final ClientPool clientPool;
    private final MessageBroadcaster messageBroadCaster;
    private final UserRegistry userRegistry;
    private final PlayerRepository playerRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final TickRegistry tickRegistry;
    private final TickClock tickClock;
    private final FixedRateTickScheduler tickScheduler;
    private final AbilityRegistry abilityRegistry;
    private final AuditService auditService;

    public SocketServer(int port, ClientPool clientPool) {
        this.port = port;
        this.clientPool = clientPool;
        this.messageBroadCaster = new MessageBroadcasterImpl(clientPool);
        this.userRegistry = new UserRegistryImpl();
        this.playerRepository = new JsonPlayerRepository();
        this.roomRepository = new InMemoryRoomRepository();
        this.roomService = new RoomService(roomRepository, RoomId.of("training-yard"));
        this.tickRegistry = new TickRegistry();
        this.tickClock = new TickClock();
        this.tickRegistry.register(tickClock);
        this.tickScheduler = new FixedRateTickScheduler(tickRegistry);
        this.abilityRegistry = loadAbilities();
        this.auditService = AuditService.create(tickClock::currentTick);
    }

    @Override
    public void run() {
        log.debug("Starting server @ port {}", port);

        tickScheduler.start();
        try (ServerSocket server = new ServerSocket(port)) {
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = server.accept();
                try {
                    SocketClient client = new SocketClient(
                        clientSocket,
                        messageBroadCaster,
                        userRegistry,
                        playerRepository,
                        roomService,
                        tickRegistry,
                        clientPool,
                        abilityRegistry,
                        auditService
                    );
                    clientPool.add(client);
                } catch (IOException e) {
                    log.error("Client connecting error", e);
                }
            }
        } catch (IOException e) {
            log.error("Server error", e);
        } finally {
            tickScheduler.stop();
            tickRegistry.clear();
        }

    }

    private AbilityRegistry loadAbilities() {
        try {
            return new AbilityRegistry(new JsonAbilityRepository().findAll());
        } catch (AbilityRepositoryException e) {
            throw new IllegalStateException("Failed to load abilities: " + e.getMessage(), e);
        }
    }
}
