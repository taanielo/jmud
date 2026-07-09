package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.audit.AuditEntry;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSink;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
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
 * Reproduces issue #334: a player who dies and re-logs in before respawning must not throw
 * {@code NullPointerException: Effect sink is required}. {@link PlayerSession#registerEffects}
 * skips a dead/absent player (leaving {@code effectSink} null), so
 * {@link PlayerSession#registerHealing} must apply the same guard rather than constructing a
 * {@code PlayerHealingTicker} with the still-null effect sink.
 */
class PlayerSessionRegisterHealingTest {

    private PersistenceQueue persistenceQueue;

    @AfterEach
    void tearDown() {
        if (persistenceQueue != null) {
            persistenceQueue.close();
        }
    }

    private static AuditService noOpAuditService() {
        AuditSink sink = new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        };
        return new AuditService(sink, Clock.systemUTC(), () -> 0L, () -> "correlation");
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
        this.persistenceQueue = new PersistenceQueue(playerRepository, noOpAuditService());
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

    private static Player newPlayer(String username) {
        User user = User.of(Username.of(username), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    @Test
    void registerHealingSkipsHealingForDeadPlayerWithoutThrowing() {
        PlayerSession session = newSession();
        session.setPlayer(newPlayer("ghost").die());

        // registerEffects skips the dead player, leaving effectSink null; registerHealing must
        // therefore also skip rather than build a PlayerHealingTicker with a null effect sink.
        session.registerEffects(sink -> { });

        assertDoesNotThrow(() -> session.registerHealing(updated -> { }));
        assertFalse(session.getPlayerTicker().isHealingEnabled(),
            "healing must be skipped for a dead player");
    }

    @Test
    void registerHealingSkipsHealingForAbsentPlayerWithoutThrowing() {
        PlayerSession session = newSession();

        assertDoesNotThrow(() -> session.registerHealing(updated -> { }));
        assertFalse(session.getPlayerTicker().isHealingEnabled(),
            "healing must be skipped when no player is present");
    }

    @Test
    void registerHealingEnablesHealingForLivePlayer() {
        PlayerSession session = newSession();
        session.setPlayer(newPlayer("alive"));

        session.registerEffects(sink -> { });
        session.registerHealing(updated -> { });

        assertTrue(session.getPlayerTicker().isHealingEnabled(),
            "healing must still be enabled for a living player");
    }
}
