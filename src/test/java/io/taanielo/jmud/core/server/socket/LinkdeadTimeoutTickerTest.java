package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.audit.AuditEntry;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSink;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.InMemoryRoomRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies that {@link LinkdeadTimeoutTicker} decrements each linkdead session's countdown per tick
 * and reaps sessions (removing them from the registry and running their expiry hook) once the
 * countdown reaches zero, leaving non-linkdead sessions untouched (issue #343).
 */
class LinkdeadTimeoutTickerTest {

    private final List<PersistenceQueue> persistenceQueues = new ArrayList<>();

    @AfterEach
    void tearDown() {
        persistenceQueues.forEach(PersistenceQueue::close);
    }

    @Test
    void countsDownEachTickAndReapsAtZero() {
        PlayerSessionRegistry registry = new PlayerSessionRegistry();
        LinkdeadTimeoutTicker ticker = new LinkdeadTimeoutTicker(registry);
        Username alice = Username.of("alice");
        PlayerSession session = newSession();
        AtomicInteger expiries = new AtomicInteger();
        session.setLinkdeadExpiryHandler(expiries::incrementAndGet);
        session.startLinkdead(3);
        registry.register(alice, session);

        ticker.tick();
        assertEquals(2, session.linkdeadTicksRemaining());
        assertTrue(registry.lookup(alice).isPresent());
        assertEquals(0, expiries.get());

        ticker.tick();
        assertEquals(1, session.linkdeadTicksRemaining());
        assertTrue(registry.lookup(alice).isPresent());

        ticker.tick();
        assertTrue(registry.lookup(alice).isEmpty(), "expired session is removed from the registry");
        assertEquals(1, expiries.get(), "expiry hook fires exactly once");
        assertFalse(session.isLinkdead());
    }

    @Test
    void leavesConnectedSessionsUntouched() {
        PlayerSessionRegistry registry = new PlayerSessionRegistry();
        LinkdeadTimeoutTicker ticker = new LinkdeadTimeoutTicker(registry);
        Username bob = Username.of("bob");
        PlayerSession session = newSession();
        AtomicInteger expiries = new AtomicInteger();
        session.setLinkdeadExpiryHandler(expiries::incrementAndGet);
        registry.register(bob, session);

        ticker.tick();
        ticker.tick();

        assertTrue(registry.lookup(bob).isPresent());
        assertEquals(0, expiries.get());
        assertFalse(session.isLinkdead());
    }

    private PlayerSession newSession() {
        PlayerRepository playerRepository = new PlayerRepository() {
            @Override
            public void savePlayer(Player player) throws RepositoryException {
            }

            @Override
            public Optional<Player> loadPlayer(Username username) {
                return Optional.empty();
            }
        };
        PersistenceQueue persistenceQueue = new PersistenceQueue(playerRepository, noOpAuditService());
        persistenceQueues.add(persistenceQueue);
        RoomService roomService = new RoomService(new InMemoryRoomRepository(), RoomId.of("training-yard"));
        EffectRepository effectRepository = new EffectRepository() {
            @Override
            public Optional<EffectDefinition> findById(EffectId id) throws EffectRepositoryException {
                return Optional.empty();
            }
        };
        EffectEngine effectEngine = new EffectEngine(effectRepository);
        RaceRepository raceRepository = new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId id) throws RaceRepositoryException {
                return Optional.empty();
            }

            @Override
            public List<Race> findAll() throws RaceRepositoryException {
                return List.of();
            }
        };
        ClassRepository classRepository = new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId id) throws ClassRepositoryException {
                return Optional.empty();
            }

            @Override
            public List<ClassDefinition> findAll() throws ClassRepositoryException {
                return List.of();
            }
        };
        HealingEngine healingEngine = new HealingEngine(effectRepository);
        HealingBaseResolver healingBaseResolver = new HealingBaseResolver(raceRepository, classRepository);
        return new PlayerSession(
            new TickRegistry(),
            persistenceQueue,
            roomService,
            updated -> { },
            effectEngine,
            effectRepository,
            healingEngine,
            healingBaseResolver
        );
    }

    private static AuditService noOpAuditService() {
        AuditSink sink = new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        };
        return new AuditService(sink, java.time.Clock.systemUTC(), () -> 0L, () -> "correlation");
    }
}
