package io.taanielo.jmud.core.mob;

import java.time.Clock;

import io.taanielo.jmud.core.audit.AuditEntry;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSink;
import io.taanielo.jmud.core.combat.CombatRandom;
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
     * Returns a deterministic {@link CombatRandom} for {@code MobRegistry} tests that drive combat
     * but do not assert on exact roll sequences.
     *
     * <p>Now that PvE combat rolls to hit (issue #589), a non-deterministic source would make these
     * tests intermittently miss. This source returns a fixed mid-range value of {@code 50} for every
     * bounded {@link CombatRandom#roll(int, int)}, which under the default combat settings always
     * lands a normal hit (≤ the base hit chance of 75) without critting (&gt; the base crit chance of
     * 5), preserving the guaranteed-hit behaviour these tests were written against. Its
     * {@link #nextDouble()} returns {@code 1.0} so probability gates (loot drop, mob wander) resolve
     * only at their boundary (drop chance {@code 1.0}), keeping those outcomes deterministic too.
     */
    static CombatRandom random() {
        return new DeterministicHitCombatRandom();
    }

    /**
     * Deterministic combat RNG backing {@link #random()}: a fixed hit-landing, non-critting roll and
     * a boundary-only probability gate. See {@link #random()} for the rationale.
     */
    private static final class DeterministicHitCombatRandom implements CombatRandom {
        private static final int FIXED_ROLL = 50;

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return Math.max(minInclusive, Math.min(maxInclusive, FIXED_ROLL));
        }

        @Override
        public double nextDouble() {
            return 1.0;
        }
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
