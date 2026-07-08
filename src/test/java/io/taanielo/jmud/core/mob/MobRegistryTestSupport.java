package io.taanielo.jmud.core.mob;

import java.time.Clock;

import io.taanielo.jmud.core.audit.AuditEntry;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSink;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.ThreadLocalCombatRandom;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.PlayerRepository;

/**
 * Shared test helper for building a {@link PersistenceQueue} backed by a fake
 * {@link PlayerRepository}, so {@code MobRegistry} tests (which construct
 * {@code MobRegistry} directly rather than via {@code GameContext}) don't each
 * repeat the same audit/queue boilerplate.
 */
final class MobRegistryTestSupport {

    private MobRegistryTestSupport() {
    }

    /**
     * Returns a {@link CombatRandom} for {@code MobRegistry} tests that do not assert on
     * exact roll sequences, preserving the historical (non-deterministic) behaviour.
     */
    static CombatRandom random() {
        return new ThreadLocalCombatRandom();
    }

    static PersistenceQueue persistenceQueueFor(PlayerRepository playerRepository) {
        AuditSink noOpSink = new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        };
        AuditService auditService = new AuditService(noOpSink, Clock.systemUTC(), () -> 0L, () -> "test-correlation");
        return new PersistenceQueue(playerRepository, auditService);
    }
}
