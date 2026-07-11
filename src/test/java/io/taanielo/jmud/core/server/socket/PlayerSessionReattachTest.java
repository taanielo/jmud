package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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
 * Verifies {@link PlayerSession}'s linkdead lifecycle (issue #343): the transitions between
 * connected, linkdead, and reattached states, the tick countdown, and the expiry hook — all without
 * any networking.
 */
class PlayerSessionReattachTest {

    private PersistenceQueue persistenceQueue;
    private TickRegistry tickRegistry;

    @AfterEach
    void tearDown() {
        if (persistenceQueue != null) {
            persistenceQueue.close();
        }
    }

    @Test
    void startLinkdeadMarksDisconnectedButKeepsTicksSubscribed() {
        PlayerSession session = newSession();
        session.setPlayer(newPlayer("alice"));
        session.setAuthenticated(true);
        session.startTicks();
        assertEquals(1, tickRegistry.snapshot().size(), "ticker subscribed on startTicks");

        session.startLinkdead(30);

        assertTrue(session.isLinkdead());
        assertFalse(session.isConnected());
        assertEquals(30, session.linkdeadTicksRemaining());
        assertEquals(1, tickRegistry.snapshot().size(),
            "going linkdead must NOT unsubscribe the composed ticker");
    }

    @Test
    void startLinkdeadClampsNonPositiveTicksToOne() {
        PlayerSession session = newSession();
        session.startLinkdead(0);
        assertEquals(1, session.linkdeadTicksRemaining());
    }

    @Test
    void tickLinkdeadCountsDownAndSignalsExpiryAtZero() {
        PlayerSession session = newSession();
        session.startLinkdead(2);

        assertFalse(session.tickLinkdead(), "first decrement leaves 1 remaining");
        assertEquals(1, session.linkdeadTicksRemaining());
        assertTrue(session.tickLinkdead(), "second decrement reaches zero -> expired");
    }

    @Test
    void tickLinkdeadIsNoopWhenNotLinkdead() {
        PlayerSession session = newSession();
        assertFalse(session.tickLinkdead());
    }

    @Test
    void reattachClearsLinkdeadAndReconnectsWithoutUnsubscribing() {
        PlayerSession session = newSession();
        session.setPlayer(newPlayer("alice"));
        session.startTicks();
        session.startLinkdead(30);

        session.reattach();

        assertFalse(session.isLinkdead());
        assertTrue(session.isConnected());
        assertEquals(0, session.linkdeadTicksRemaining());
        assertEquals(1, tickRegistry.snapshot().size(),
            "reattach must not touch the tick subscription");
    }

    @Test
    void expireLinkdeadRunsHandlerAndClearsState() {
        PlayerSession session = newSession();
        session.startLinkdead(1);
        AtomicInteger expiries = new AtomicInteger();
        session.setLinkdeadExpiryHandler(expiries::incrementAndGet);

        session.expireLinkdead();

        assertEquals(1, expiries.get());
        assertFalse(session.isLinkdead());
        assertEquals(0, session.linkdeadTicksRemaining());
    }

    @Test
    void unsubscribeTicksStopsTheComposedTicker() {
        PlayerSession session = newSession();
        session.setPlayer(newPlayer("alice"));
        session.startTicks();
        assertEquals(1, tickRegistry.snapshot().size());

        session.unsubscribeTicks();

        assertEquals(0, tickRegistry.snapshot().size());
    }

    // --- AFK / away status (issue #464) ---

    @Test
    void newSessionIsNotAway() {
        PlayerSession session = newSession();
        assertFalse(session.isAway());
        assertNull(session.awayMessage());
    }

    @Test
    void setAwayWithoutMessageMarksAwayWithNoReason() {
        PlayerSession session = newSession();
        session.setAway(null);
        assertTrue(session.isAway());
        assertNull(session.awayMessage());
    }

    @Test
    void setAwayStoresTrimmedCustomMessage() {
        PlayerSession session = newSession();
        session.setAway("  grabbing coffee  ");
        assertTrue(session.isAway());
        assertEquals("grabbing coffee", session.awayMessage());
    }

    @Test
    void clearAwayResetsBothFlagAndMessage() {
        PlayerSession session = newSession();
        session.setAway("lunch");
        session.clearAway();
        assertFalse(session.isAway());
        assertNull(session.awayMessage());
    }

    @Test
    void reattachClearsAwayStatus() {
        PlayerSession session = newSession();
        session.setPlayer(newPlayer("alice"));
        session.startTicks();
        session.setAway("brb");
        session.startLinkdead(30);

        session.reattach();

        assertFalse(session.isAway(), "Away status must not survive a reconnect");
        assertNull(session.awayMessage());
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
        this.tickRegistry = new TickRegistry();
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
            tickRegistry,
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

    private static AuditService noOpAuditService() {
        AuditSink sink = new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        };
        return new AuditService(sink, java.time.Clock.systemUTC(), () -> 0L, () -> "correlation");
    }
}
