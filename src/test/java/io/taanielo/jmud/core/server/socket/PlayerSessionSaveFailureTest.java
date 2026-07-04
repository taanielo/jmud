package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

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
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.InMemoryRoomRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies that {@link PlayerSession} surfaces player-save failures via its
 * save-failure hook, rather than silently swallowing them, satisfying the
 * "player warning + audit" acceptance criterion from a caller's perspective
 * (see issue #169).
 */
class PlayerSessionSaveFailureTest {

    private PlayerSession newSession(PlayerRepository playerRepository) {
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
            playerRepository,
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

    /** A player repository whose {@code savePlayer} always fails. */
    private static final class FailingPlayerRepository implements PlayerRepository {
        private final AtomicInteger saveAttempts = new AtomicInteger();

        @Override
        public void savePlayer(Player player) throws RepositoryException {
            saveAttempts.incrementAndGet();
            throw new RepositoryException("disk full (simulated)");
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.empty();
        }
    }

    @Test
    void replacePlayerNotifiesSaveFailureHandlerWhenSaveFails() {
        FailingPlayerRepository repository = new FailingPlayerRepository();
        PlayerSession session = newSession(repository);
        List<Player> notifiedFailures = new ArrayList<>();
        session.setSaveFailureHandler(notifiedFailures::add);

        Player player = newPlayer("sparky");
        session.replacePlayer(player);

        assertEquals(1, notifiedFailures.size(), "Caller must be notified so it can warn the player");
        assertEquals(player.getUsername(), notifiedFailures.get(0).getUsername());
    }

    @Test
    void closeRetriesSaveOnceThenNotifiesSaveFailureHandler() {
        FailingPlayerRepository repository = new FailingPlayerRepository();
        PlayerSession session = newSession(repository);
        List<Player> notifiedFailures = new ArrayList<>();
        session.setSaveFailureHandler(notifiedFailures::add);

        Player player = newPlayer("questy");
        session.setPlayer(player);
        session.setAuthenticated(true);
        session.startTicks();

        session.close();

        assertEquals(2, repository.saveAttempts.get(), "QUIT/disconnect must retry the save once before giving up");
        assertEquals(1, notifiedFailures.size(), "Caller must still be warned after the retry fails");
        assertTrue(notifiedFailures.get(0).getUsername().equals(player.getUsername()));
    }
}
