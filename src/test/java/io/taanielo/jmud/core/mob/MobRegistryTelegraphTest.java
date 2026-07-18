package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.combat.RangeType;
import io.taanielo.jmud.core.combat.WeaponType;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Verifies the boss special-attack telegraph mechanic (see
 * {@link AttackDefinition#telegraphTicks()}): a telegraphing special defers its blow by the configured
 * number of AI ticks, announces a warning, lands unchanged damage once the window elapses, is
 * cancelled with no damage when the fight ends mid-wind-up, and leaves non-telegraphed specials
 * behaving exactly as before.
 */
class MobRegistryTelegraphTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId NORMAL_ATTACK_ID = AttackId.of("attack.normal");
    private static final AttackId SPECIAL_ATTACK_ID = AttackId.of("attack.special");
    private static final int TELEGRAPH_TICKS = 2;
    private static final String TELEGRAPH_LINE = "The Forest Troll begins winding up troll smash!";

    private static final AttackDefinition NORMAL_ATTACK =
        new AttackDefinition(NORMAL_ATTACK_ID, "club", 2, 2, 0, 0, 0, List.of());
    private static final AttackDefinition TELEGRAPH_SPECIAL = new AttackDefinition(
        SPECIAL_ATTACK_ID, "troll smash", 9, 9, 0, 0, 0,
        List.of(new MessageSpec(MessagePhase.TELEGRAPH, MessageChannel.SELF, TELEGRAPH_LINE)),
        WeaponType.BLUNT, null, RangeType.MELEE, DamageType.PHYSICAL, TELEGRAPH_TICKS);
    private static final AttackDefinition INSTANT_SPECIAL = new AttackDefinition(
        SPECIAL_ATTACK_ID, "troll smash", 9, 9, 0, 0, 0, List.of(),
        WeaponType.BLUNT, null, RangeType.MELEE, DamageType.PHYSICAL, 0);

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate bossTemplate() {
        return new MobTemplate(
            MobId.of("mob.boss"), "Forest Troll", 100, NORMAL_ATTACK_ID, SPECIAL_ATTACK_ID,
            true, List.of(), ROOM_ID, 1, 10, 5, null, null, false);
    }

    private MobRegistry buildRegistry(
        AttackDefinition special, Player target, PlayerEventBus bus, PlayerRepository playerRepo,
        PersistenceQueue persistenceQueue
    ) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(bossTemplate()));
        AttackRepository attackRepo = new StubAttackRepository(
            Map.of(NORMAL_ATTACK_ID, NORMAL_ATTACK, SPECIAL_ATTACK_ID, special));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, persistenceQueue, bus,
            MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    private int currentHp(PlayerRepository repo, Username username) {
        return repo.loadPlayer(username).orElseThrow().getVitals().hp();
    }

    @Test
    void telegraphDelaysHitThenLandsWithFullDamage() {
        Player target = player("hero");
        PlayerEventBus bus = new PlayerEventBus();
        PlayerRepository playerRepo = new StubPlayerRepository(target);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(TELEGRAPH_SPECIAL, target, bus, playerRepo, persistenceQueue);
        Username username = target.getUsername();

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(username, received::set);

        // Announce tick: the telegraph is broadcast and no damage is dealt.
        registry.tick();
        GameActionResult announce = received.get();
        assertNotNull(announce, "Expected a telegraph announcement on the first tick");
        assertNull(announce.updatedSource(), "Telegraph announcement must not damage or save the player");
        assertTrue(announce.messages().stream()
                .map(GameMessage::text).anyMatch(TELEGRAPH_LINE::equals),
            "Expected the authored telegraph line to be shown to the target");
        assertEquals(20, currentHp(playerRepo, username), "No damage on the announce tick");

        // Wind-up tick: still channeling, no message and no damage.
        received.set(null);
        registry.tick();
        assertNull(received.get(), "Expected no message while the boss is still winding up");
        assertEquals(20, currentHp(playerRepo, username), "No damage while winding up");

        // Resolve tick: the deferred special lands with its full, unchanged damage.
        received.set(null);
        tickAndAwaitPersist(registry, persistenceQueue);
        GameActionResult hit = received.get();
        assertNotNull(hit);
        assertNotNull(hit.updatedSource());
        assertEquals(20 - 9, hit.updatedSource().getVitals().hp(),
            "Expected the telegraphed special to land its full damage once the window elapsed");

        // Subsequent tick: the special is spent, so the mob falls back to its normal attack.
        received.set(null);
        tickAndAwaitPersist(registry, persistenceQueue);
        assertEquals(20 - 9 - 2, received.get().updatedSource().getVitals().hp(),
            "Expected the mob to use its normal attack once the special has been spent");
    }

    @Test
    void telegraphCancelledWithNoDamageWhenMobDiesMidWindUp() {
        Player target = player("hero2");
        PlayerEventBus bus = new PlayerEventBus();
        PlayerRepository playerRepo = new StubPlayerRepository(target);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(TELEGRAPH_SPECIAL, target, bus, playerRepo, persistenceQueue);
        Username username = target.getUsername();

        registry.tick(); // announce
        assertEquals(20, currentHp(playerRepo, username));

        // The boss dies before its wind-up elapses.
        MobInstance mob = registry.getMobsInRoom(ROOM_ID).get(0);
        mob.takeDamage(mob.currentHp());
        assertFalse(mob.isAlive());

        // Several ticks pass; the telegraphed blow never lands.
        registry.tick();
        registry.tick();
        assertEquals(20, currentHp(playerRepo, username),
            "A dead mob's telegraphed attack must never land");
    }

    @Test
    void telegraphCancelledWhenTargetLeavesCombatMidWindUp() {
        Player target = player("hero3");
        PlayerEventBus bus = new PlayerEventBus();
        PlayerRepository playerRepo = new StubPlayerRepository(target);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(TELEGRAPH_SPECIAL, target, bus, playerRepo, persistenceQueue);
        Username username = target.getUsername();

        registry.tick(); // announce
        MobInstance mob = registry.getMobsInRoom(ROOM_ID).get(0);
        assertTrue(mob.hasPendingTelegraph(), "Expected a pending telegraph after the announce tick");
        assertEquals(20, currentHp(playerRepo, username));

        // The player flees, ending the encounter; the pending telegraph must be cancelled.
        registry.fleeCombat(username);
        assertFalse(mob.hasPendingTelegraph(),
            "Ending the encounter mid-wind-up must cancel the pending telegraph");
    }

    @Test
    void specialWithoutTelegraphFiresInstantlyAsBefore() {
        Player target = player("hero4");
        PlayerEventBus bus = new PlayerEventBus();
        PlayerRepository playerRepo = new StubPlayerRepository(target);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(INSTANT_SPECIAL, target, bus, playerRepo, persistenceQueue);
        Username username = target.getUsername();

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(username, received::set);

        tickAndAwaitPersist(registry, persistenceQueue);
        assertEquals(20 - 9, received.get().updatedSource().getVitals().hp(),
            "A special with no telegraph must fire instantly on the first swing, exactly as before");
    }

    private void tickAndAwaitPersist(MobRegistry registry, PersistenceQueue persistenceQueue) {
        registry.tick();
        assertTrue(persistenceQueue.flush(Duration.ofSeconds(5)), "Expected persistence queue to drain");
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubMobTemplateRepository(List<MobTemplate> templates)
        implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() { return templates; }
    }

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks)
        implements AttackRepository {
        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static class StubItemRepository implements ItemRepository {
        private final Map<ItemId, Item> items;

        StubItemRepository(Map<ItemId, Item> items) { this.items = Map.copyOf(items); }

        @Override
        public void save(Item item) throws RepositoryException {}

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.ofNullable(items.get(id));
        }
    }

    private static class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();

        StubPlayerRepository(Player initial) {
            store.put(initial.getUsername(), initial);
        }

        @Override
        public void savePlayer(Player player) { store.put(player.getUsername(), player); }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }

    private static class StubRoomRepository implements RoomRepository {
        private final Room room;

        StubRoomRepository(RoomId roomId) {
            this.room = new Room(
                roomId, "Test Room", "A featureless void.", Map.of(), List.of(), List.of());
        }

        @Override
        public void save(Room room) throws RepositoryException {}

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
