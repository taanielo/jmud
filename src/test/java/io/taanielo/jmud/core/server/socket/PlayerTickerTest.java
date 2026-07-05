package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
import io.taanielo.jmud.core.player.PlayerRespawnTicker;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.player.RestingTicker;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.tick.system.CooldownSystem;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.InMemoryRoomRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Unit tests for {@link PlayerTicker}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Stage execution order: commands (1) before cooldowns (2), respawn (3),
 *       effects (4), healing (5), and resting (6).</li>
 *   <li>REST toggle: {@link PlayerTicker#enableResting}/{@link PlayerTicker#disableResting}
 *       correctly gate the resting stage.</li>
 *   <li>Exactly-one-subscription: {@link PlayerSession#startTicks()} registers
 *       a single entry in the {@link TickRegistry}.</li>
 * </ul>
 */
class PlayerTickerTest {

    private PersistenceQueue persistenceQueue;

    @AfterEach
    void tearDown() {
        if (persistenceQueue != null) {
            persistenceQueue.close();
        }
    }

    /** Returns an idle respawn ticker whose player supplier returns null (nothing to do). */
    private PlayerRespawnTicker idleRespawnTicker() {
        RoomService roomService = new RoomService(new InMemoryRoomRepository(), RoomId.of("start"));
        return new PlayerRespawnTicker(() -> null, p -> {}, roomService, 5);
    }

    /** Creates a resting player at maximum vitals — the resting ticker will auto-fire on the first tick. */
    private Player fullRestingPlayer(String username) {
        User user = User.of(Username.of(username), Password.hash("pw", 1));
        return Player.of(user, "%hp>")
            .withVitals(new PlayerVitals(20, 20, 20, 20, 20, 20))
            .withResting(true);
    }

    private static AuditService noOpAuditService() {
        AuditSink sink = new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        };
        return new AuditService(sink, Clock.systemUTC(), () -> 0L, () -> "test-correlation");
    }

    private PlayerSession newSession(TickRegistry registry) {
        this.persistenceQueue = new PersistenceQueue(new NoOpPlayerRepository(), noOpAuditService());
        RoomService roomService = new RoomService(new InMemoryRoomRepository(), RoomId.of("start"));
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
            registry,
            persistenceQueue,
            roomService,
            updated -> {},
            effectEngine,
            effectRepository,
            healingEngine,
            healingBaseResolver
        );
    }

    // ── stage execution order ──────────────────────────────────────────────────

    /**
     * Stage 1 (commands) must execute before Stage 6 (resting) within a single tick.
     *
     * <p>The test proves the documented order by observing that a queued command fires
     * before the resting callback when both are active in the same tick.
     */
    @Test
    void commandStageRunsBeforeRestingStage() {
        List<String> order = new ArrayList<>();

        // Stage 1: command queue
        PlayerCommandQueue queue = new PlayerCommandQueue();
        queue.enqueue(() -> order.add("stage1-commands"));

        CooldownSystem cooldowns = new CooldownSystem();
        PlayerRespawnTicker respawnTicker = idleRespawnTicker();

        PlayerTicker ticker = new PlayerTicker(queue, cooldowns, respawnTicker);

        // Stage 6: resting — a fully-rested player triggers onFullyRested immediately.
        AtomicReference<Player> ref = new AtomicReference<>(fullRestingPlayer("hero"));
        RestingTicker restingTicker = new RestingTicker(
            ref::get,
            ref::set,
            (msg, woken) -> {
                ref.set(woken);
                order.add("stage6-resting");
            },
            2, 2, 2
        );
        ticker.enableResting(restingTicker);

        ticker.tick();

        assertEquals(List.of("stage1-commands", "stage6-resting"), order,
            "Stage 1 (commands) must fire before Stage 6 (resting) within a single tick");
    }

    /**
     * Commands enqueued after the queue has drained run only on the next tick.
     */
    @Test
    void commandsQueuedAfterDrainRunOnNextTick() {
        List<String> order = new ArrayList<>();

        PlayerCommandQueue queue = new PlayerCommandQueue();
        queue.enqueue(() -> order.add("tick1-command"));

        CooldownSystem cooldowns = new CooldownSystem();
        PlayerTicker ticker = new PlayerTicker(queue, cooldowns, idleRespawnTicker());

        ticker.tick();
        assertEquals(List.of("tick1-command"), order);

        queue.enqueue(() -> order.add("tick2-command"));
        ticker.tick();
        assertEquals(List.of("tick1-command", "tick2-command"), order,
            "Command enqueued after first tick should run on second tick only");
    }

    // ── REST toggling ──────────────────────────────────────────────────────────

    @Test
    void restingStageInitiallyDisabled() {
        PlayerCommandQueue queue = new PlayerCommandQueue();
        CooldownSystem cooldowns = new CooldownSystem();
        PlayerTicker ticker = new PlayerTicker(queue, cooldowns, idleRespawnTicker());

        assertFalse(ticker.isRestingEnabled(), "Resting stage must be disabled on construction");
    }

    @Test
    void enableRestingActivatesStage() {
        PlayerCommandQueue queue = new PlayerCommandQueue();
        CooldownSystem cooldowns = new CooldownSystem();
        PlayerTicker ticker = new PlayerTicker(queue, cooldowns, idleRespawnTicker());

        AtomicReference<Player> ref = new AtomicReference<>(fullRestingPlayer("hero"));
        RestingTicker restingTicker = new RestingTicker(
            ref::get, ref::set, (msg, woken) -> ref.set(woken), 2, 2, 2
        );

        ticker.enableResting(restingTicker);
        assertTrue(ticker.isRestingEnabled(), "Resting stage must be enabled after enableResting()");
    }

    @Test
    void disableRestingDeactivatesStage() {
        PlayerCommandQueue queue = new PlayerCommandQueue();
        CooldownSystem cooldowns = new CooldownSystem();
        PlayerTicker ticker = new PlayerTicker(queue, cooldowns, idleRespawnTicker());

        AtomicReference<Player> ref = new AtomicReference<>(fullRestingPlayer("hero"));
        RestingTicker restingTicker = new RestingTicker(
            ref::get, ref::set, (msg, woken) -> ref.set(woken), 2, 2, 2
        );

        ticker.enableResting(restingTicker);
        assertTrue(ticker.isRestingEnabled());

        ticker.disableResting();
        assertFalse(ticker.isRestingEnabled(), "Resting stage must be disabled after disableResting()");
    }

    @Test
    void disabledRestingStageDoesNotInvokeRestingTicker() {
        List<String> invocations = new ArrayList<>();

        PlayerCommandQueue queue = new PlayerCommandQueue();
        CooldownSystem cooldowns = new CooldownSystem();
        PlayerTicker ticker = new PlayerTicker(queue, cooldowns, idleRespawnTicker());

        AtomicReference<Player> ref = new AtomicReference<>(fullRestingPlayer("hero"));
        RestingTicker restingTicker = new RestingTicker(
            ref::get,
            ref::set,
            (msg, woken) -> { ref.set(woken); invocations.add("rested"); },
            2, 2, 2
        );
        ticker.enableResting(restingTicker);
        ticker.disableResting();

        ticker.tick();

        assertTrue(invocations.isEmpty(), "Resting ticker must not run after disableResting()");
    }

    // ── effects and healing initial state ─────────────────────────────────────

    @Test
    void effectsStageInitiallyDisabled() {
        PlayerCommandQueue queue = new PlayerCommandQueue();
        CooldownSystem cooldowns = new CooldownSystem();
        PlayerTicker ticker = new PlayerTicker(queue, cooldowns, idleRespawnTicker());

        assertFalse(ticker.isEffectsEnabled(), "Effects stage must be disabled on construction");
    }

    @Test
    void healingStageInitiallyDisabled() {
        PlayerCommandQueue queue = new PlayerCommandQueue();
        CooldownSystem cooldowns = new CooldownSystem();
        PlayerTicker ticker = new PlayerTicker(queue, cooldowns, idleRespawnTicker());

        assertFalse(ticker.isHealingEnabled(), "Healing stage must be disabled on construction");
    }

    // ── exactly-one-subscription semantics ────────────────────────────────────

    /**
     * {@link PlayerSession#startTicks()} must register exactly one subscription
     * in the {@link TickRegistry}; the registered entry must be the composed
     * {@link PlayerTicker}, not multiple individual stage tickables.
     */
    @Test
    void startTicksRegistersExactlyOneEntry() {
        TickRegistry registry = new TickRegistry();
        PlayerSession session = newSession(registry);

        assertEquals(0, registry.snapshot().size(), "No subscriptions before startTicks()");

        session.startTicks();

        assertEquals(1, registry.snapshot().size(),
            "Exactly one subscription must be registered after startTicks()");
        assertTrue(registry.snapshot().get(0) instanceof PlayerTicker,
            "The registered tickable must be the composed PlayerTicker");
    }

    /**
     * After {@link PlayerSession#close()} the single subscription must be removed from
     * the registry, leaving the global tick list clean.
     */
    @Test
    void closeUnregistersTheSubscription() {
        TickRegistry registry = new TickRegistry();
        PlayerSession session = newSession(registry);

        session.startTicks();
        assertEquals(1, registry.snapshot().size());

        session.close();

        assertEquals(0, registry.snapshot().size(),
            "close() must unregister the composed PlayerTicker subscription");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static final class NoOpPlayerRepository implements PlayerRepository {
        @Override
        public void savePlayer(Player player) throws RepositoryException {
            // no-op; tests do not care about persistence
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.empty();
        }
    }
}
