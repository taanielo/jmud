package io.taanielo.jmud.core.server.socket;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.messaging.SystemNoticeMessage;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Executes the server's orderly shutdown sequence: stop accepting new
 * connections, notify connected clients, stop the tick scheduler, save every
 * online player, flush and close the audit sink, and clear tick registrations.
 *
 * <p>The whole sequence runs synchronously on the calling thread (the JVM
 * shutdown-hook thread) and is bounded, so a {@code SIGTERM} does not force a
 * container/systemd stop to kill the process mid-save. Once the tick
 * scheduler has stopped, no tick thread exists, so subsequent state reads are
 * race-free.
 */
@Slf4j
public class ShutdownCoordinator {

    private static final Duration DEFAULT_AUDIT_FLUSH_TIMEOUT = Duration.ofSeconds(2);

    private final List<Server> servers;
    private final ClientPool clientPool;
    private final FixedRateTickScheduler tickScheduler;
    private final TickRegistry tickRegistry;
    private final PlayerRepository playerRepository;
    private final AuditService auditService;
    private final Duration auditFlushTimeout;

    public ShutdownCoordinator(
        List<Server> servers,
        ClientPool clientPool,
        FixedRateTickScheduler tickScheduler,
        TickRegistry tickRegistry,
        PlayerRepository playerRepository,
        AuditService auditService
    ) {
        this(servers, clientPool, tickScheduler, tickRegistry, playerRepository, auditService, DEFAULT_AUDIT_FLUSH_TIMEOUT);
    }

    public ShutdownCoordinator(
        List<Server> servers,
        ClientPool clientPool,
        FixedRateTickScheduler tickScheduler,
        TickRegistry tickRegistry,
        PlayerRepository playerRepository,
        AuditService auditService,
        Duration auditFlushTimeout
    ) {
        this.servers = List.copyOf(Objects.requireNonNull(servers, "Servers are required"));
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
        this.tickScheduler = Objects.requireNonNull(tickScheduler, "Tick scheduler is required");
        this.tickRegistry = Objects.requireNonNull(tickRegistry, "Tick registry is required");
        this.playerRepository = Objects.requireNonNull(playerRepository, "Player repository is required");
        this.auditService = Objects.requireNonNull(auditService, "Audit service is required");
        this.auditFlushTimeout = Objects.requireNonNull(auditFlushTimeout, "Audit flush timeout is required");
    }

    /**
     * Runs the shutdown sequence in order: stop accepting, notify clients,
     * stop ticks, save online players, flush audit, clear tick registrations.
     * A failure for one step or one player must not prevent the remaining
     * steps/players from being processed.
     */
    public void shutdown() {
        log.info("Shutdown sequence starting");
        stopAccepting();
        notifyClientsOfShutdown();
        stopTickScheduler();
        saveOnlinePlayers();
        flushAudit();
        tickRegistry.clear();
        log.info("Shutdown sequence complete");
    }

    private void stopAccepting() {
        for (Server server : servers) {
            try {
                server.stop();
            } catch (RuntimeException e) {
                log.warn("Failed to stop server {}", server, e);
            }
        }
    }

    private void notifyClientsOfShutdown() {
        SystemNoticeMessage notice = new SystemNoticeMessage("Server is shutting down.");
        for (Client client : clientPool.clients()) {
            try {
                client.sendMessage(notice);
            } catch (RuntimeException e) {
                log.warn("Failed to notify client of shutdown", e);
            }
        }
    }

    private void stopTickScheduler() {
        tickScheduler.stop();
    }

    private void saveOnlinePlayers() {
        for (Client client : clientPool.clients()) {
            client.currentPlayer().ifPresent(this::saveOrLog);
        }
    }

    private void saveOrLog(Player player) {
        try {
            playerRepository.savePlayer(player);
            log.info("Saved player {} during shutdown", player.getUsername());
        } catch (RepositoryException e) {
            log.error("Failed to save player {} during shutdown", player.getUsername(), e);
        }
    }

    private void flushAudit() {
        auditService.shutdown(auditFlushTimeout);
    }
}
