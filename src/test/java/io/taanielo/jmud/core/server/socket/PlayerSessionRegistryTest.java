package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

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
 * Verifies the register/lookup/remove semantics of {@link PlayerSessionRegistry} (issue #343).
 */
class PlayerSessionRegistryTest {

    private final List<PersistenceQueue> persistenceQueues = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        persistenceQueues.forEach(PersistenceQueue::close);
    }

    @Test
    void registerThenLookupReturnsSameSession() {
        PlayerSessionRegistry registry = new PlayerSessionRegistry();
        Username alice = Username.of("alice");
        PlayerSession session = newSession();

        registry.register(alice, session);

        assertTrue(registry.lookup(alice).isPresent());
        assertSame(session, registry.lookup(alice).orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    void lookupUnknownUsernameIsEmpty() {
        PlayerSessionRegistry registry = new PlayerSessionRegistry();
        assertTrue(registry.lookup(Username.of("nobody")).isEmpty());
    }

    @Test
    void removeForgetsSession() {
        PlayerSessionRegistry registry = new PlayerSessionRegistry();
        Username alice = Username.of("alice");
        registry.register(alice, newSession());

        registry.remove(alice);

        assertTrue(registry.lookup(alice).isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    void removeIfOnlyRemovesTheMatchingSession() {
        PlayerSessionRegistry registry = new PlayerSessionRegistry();
        Username alice = Username.of("alice");
        PlayerSession original = newSession();
        PlayerSession replacement = newSession();
        registry.register(alice, original);
        registry.register(alice, replacement);

        // A stale session must not be able to evict the newer one mapped under the same name.
        registry.removeIf(alice, original);
        assertSame(replacement, registry.lookup(alice).orElseThrow());

        registry.removeIf(alice, replacement);
        assertTrue(registry.lookup(alice).isEmpty());
    }

    @Test
    void entriesSnapshotReflectsRegisteredSessions() {
        PlayerSessionRegistry registry = new PlayerSessionRegistry();
        registry.register(Username.of("alice"), newSession());
        registry.register(Username.of("bob"), newSession());

        assertEquals(2, registry.entries().size());
        assertFalse(registry.entries().isEmpty());
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
