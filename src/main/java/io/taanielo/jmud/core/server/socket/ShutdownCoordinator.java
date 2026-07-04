package io.taanielo.jmud.core.server.socket;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.messaging.SystemNoticeMessage;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickRegistry;

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
    private static final Duration PERSISTENCE_FLUSH_TIMEOUT = Duration.ofSeconds(10);

    private final List<Server> servers;
    private final ClientPool clientPool;
    private final FixedRateTickScheduler tickScheduler;
    private final TickRegistry tickRegistry;
    private final PersistenceQueue persistenceQueue;
    private final AuditService auditService;
    private final Duration auditFlushTimeout;

    public ShutdownCoordinator(
        List<Server> servers,
        ClientPool clientPool,
        FixedRateTickScheduler tickScheduler,
        TickRegistry tickRegistry,
        PersistenceQueue persistenceQueue,
        AuditService auditService
    ) {
        this(servers, clientPool, tickScheduler, tickRegistry, persistenceQueue, auditService, DEFAULT_AUDIT_FLUSH_TIMEOUT);
    }

    public ShutdownCoordinator(
        List<Server> servers,
        ClientPool clientPool,
        FixedRateTickScheduler tickScheduler,
        TickRegistry tickRegistry,
        PersistenceQueue persistenceQueue,
        AuditService auditService,
        Duration auditFlushTimeout
    ) {
        this.servers = List.copyOf(Objects.requireNonNull(servers, "Servers are required"));
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
        this.tickScheduler = Objects.requireNonNull(tickScheduler, "Tick scheduler is required");
        this.tickRegistry = Objects.requireNonNull(tickRegistry, "Tick registry is required");
        this.persistenceQueue = Objects.requireNonNull(persistenceQueue, "Persistence queue is required");
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

    private void saveOnlinePlayers() {
        for (Client client : clientPool.clients()) {
            client.currentPlayer().ifPresent(this::enqueueSave);
        }
        boolean flushed = persistenceQueue.flush(PERSISTENCE_FLUSH_TIMEOUT);
        if (!flushed) {
            log.error("Persistence queue did not drain within {} during shutdown; some player saves may be lost",
                PERSISTENCE_FLUSH_TIMEOUT);
        }
        persistenceQueue.close();
    }

    private void enqueueSave(Player player) {
        persistenceQueue.enqueueSave(player);
        log.info("Enqueued save for player {} during shutdown", player.getUsername());
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

    private void flushAudit() {
        auditService.shutdown(auditFlushTimeout);
    }
}
