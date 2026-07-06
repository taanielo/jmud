package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
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
 * Verifies boss-tier mob AI's use of a configured special attack
 * (see {@link MobTemplate#specialAttackId()}): it fires at most once per combat
 * encounter, resets when a new encounter begins, and mobs without a configured
 * special attack fall back to their normal attack with no behaviour change.
 */
class MobRegistrySpecialAbilityTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId NORMAL_ATTACK_ID = AttackId.of("attack.normal");
    private static final AttackId SPECIAL_ATTACK_ID = AttackId.of("attack.special");
    private static final AttackDefinition NORMAL_ATTACK =
        new AttackDefinition(NORMAL_ATTACK_ID, "club", 2, 2, 0, 0, 0, List.of());
    private static final AttackDefinition SPECIAL_ATTACK =
        new AttackDefinition(SPECIAL_ATTACK_ID, "troll smash", 9, 9, 0, 0, 0, List.of());

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate bossTemplate() {
        return new MobTemplate(
            MobId.of("mob.boss"),
            "Forest Troll",
            100,
            NORMAL_ATTACK_ID,
            SPECIAL_ATTACK_ID,
            true,
            List.of(),
            ROOM_ID,
            1,
            10,
            5,
            null,
            null,
            false
        );
    }

    private MobTemplate noSpecialTemplate() {
        return new MobTemplate(
            MobId.of("mob.plain"),
            "Goblin",
            100,
            NORMAL_ATTACK_ID,
            null,
            true,
            List.of(),
            ROOM_ID,
            1,
            10,
            5,
            null,
            null,
            false
        );
    }

    private MobRegistry buildRegistry(
        MobTemplate template, Player target, PlayerEventBus bus, PlayerRepository playerRepo,
        PersistenceQueue persistenceQueue
    ) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(
            Map.of(NORMAL_ATTACK_ID, NORMAL_ATTACK, SPECIAL_ATTACK_ID, SPECIAL_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, persistenceQueue, bus);
        registry.init();
        return registry;
    }

    /**
     * Runs a tick and blocks until the resulting player save (if any) has been
     * written back to the repository, so the next tick reads an up-to-date snapshot
     * rather than racing the persistence queue's background writer.
     */
    private void tickAndAwaitPersist(MobRegistry registry, PersistenceQueue persistenceQueue) {
        registry.tick();
        assertTrue(persistenceQueue.flush(Duration.ofSeconds(5)), "Expected persistence queue to drain");
    }

    @Test
    void specialAttackUsedOnceThenFallsBackToNormalAttack() {
        Player target = player("hero");
        PlayerEventBus bus = new PlayerEventBus();
        PlayerRepository playerRepo = new StubPlayerRepository(target);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(bossTemplate(), target, bus, playerRepo, persistenceQueue);

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);

        tickAndAwaitPersist(registry, persistenceQueue);
        GameActionResult first = received.get();
        assertNotNull(first.updatedSource(), "Expected damaged player snapshot on first tick");
        assertEquals(20 - 9, first.updatedSource().getVitals().hp(),
            "Expected the mob's special attack to fire on the first hit of the encounter");

        received.set(null);
        tickAndAwaitPersist(registry, persistenceQueue);
        GameActionResult second = received.get();
        assertNotNull(second.updatedSource());
        assertEquals(20 - 9 - 2, second.updatedSource().getVitals().hp(),
            "Expected the mob to use its normal attack once the special has been used");

        received.set(null);
        tickAndAwaitPersist(registry, persistenceQueue);
        GameActionResult third = received.get();
        assertNotNull(third.updatedSource());
        assertEquals(20 - 9 - 2 - 2, third.updatedSource().getVitals().hp(),
            "Expected the special attack to remain unavailable for the rest of the encounter");
    }

    @Test
    void specialAttackResetsOnNewCombatEncounter() {
        Player target = player("hero2");
        PlayerEventBus bus = new PlayerEventBus();
        PlayerRepository playerRepo = new StubPlayerRepository(target);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(bossTemplate(), target, bus, playerRepo, persistenceQueue);

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);

        tickAndAwaitPersist(registry, persistenceQueue);
        assertEquals(20 - 9, received.get().updatedSource().getVitals().hp());

        // End the encounter (e.g. the player flees), which should reset the "used" flag.
        registry.fleeCombat(target.getUsername());

        received.set(null);
        tickAndAwaitPersist(registry, persistenceQueue);
        GameActionResult afterNewEncounter = received.get();
        assertNotNull(afterNewEncounter.updatedSource());
        assertEquals(20 - 9 - 9, afterNewEncounter.updatedSource().getVitals().hp(),
            "Expected the special attack to be available again in a new combat encounter");
    }

    @Test
    void mobWithoutSpecialAttackAlwaysUsesNormalAttack() {
        Player target = player("hero3");
        PlayerEventBus bus = new PlayerEventBus();
        PlayerRepository playerRepo = new StubPlayerRepository(target);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(noSpecialTemplate(), target, bus, playerRepo, persistenceQueue);

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);

        tickAndAwaitPersist(registry, persistenceQueue);
        assertEquals(20 - 2, received.get().updatedSource().getVitals().hp());

        received.set(null);
        tickAndAwaitPersist(registry, persistenceQueue);
        assertEquals(20 - 2 - 2, received.get().updatedSource().getVitals().hp(),
            "Expected mobs with no configured special ability to always use the normal attack");
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
