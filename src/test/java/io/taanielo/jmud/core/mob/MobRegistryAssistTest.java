package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatSettings;
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
 * Verifies the {@code ASSIST} command entry point ({@link MobRegistry#processPlayerAssist}): joining
 * a teammate's fight, and the failure paths (target absent, target idle, target's mob already dead).
 */
class MobRegistryAssistTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate templateWithHp(int maxHp) {
        return new MobTemplate(
            MobId.of("mob.rat"), "Rat", maxHp, null, null, false, List.of(),
            ROOM_ID, 1, 10, 5, null, null, false);
    }

    private MobRegistry buildRegistry(MobTemplate template, RoomService roomService, PlayerRepository playerRepo) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository();
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, persistenceQueue,
            new PlayerEventBus(), MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    private String firstMessage(GameActionResult result) {
        return result.messages().get(0).text();
    }

    @Test
    void assist_engagesAssisterAgainstTargetsMob() {
        Player leader = player("Riona");
        Player helper = player("Bran");
        StubPlayerRepository playerRepo = new StubPlayerRepository(leader, helper);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(leader.getUsername());
        roomService.ensurePlayerLocation(helper.getUsername());
        MobRegistry registry = buildRegistry(templateWithHp(100), roomService, playerRepo);

        // Leader engages the mob first so it becomes their active combat target.
        registry.processPlayerAttack(leader, "Rat", ROOM_ID);
        assertTrue(registry.isInCombat(leader.getUsername()));
        assertFalse(registry.isInCombat(helper.getUsername()));

        GameActionResult result = registry.processPlayerAssist(helper, "Riona", ROOM_ID);

        assertEquals("You join the fight against the Rat!", firstMessage(result));
        assertTrue(registry.isInCombat(helper.getUsername()),
            "Assister should now be engaged with the leader's mob");
    }

    @Test
    void assist_failsWhenTargetNotInRoom() {
        Player helper = player("Bran");
        StubPlayerRepository playerRepo = new StubPlayerRepository(helper);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(helper.getUsername());
        MobRegistry registry = buildRegistry(templateWithHp(100), roomService, playerRepo);

        GameActionResult result = registry.processPlayerAssist(helper, "Riona", ROOM_ID);

        assertEquals("You don't see Riona here.", firstMessage(result));
        assertFalse(registry.isInCombat(helper.getUsername()));
    }

    @Test
    void assist_failsWhenTargetNotFighting() {
        Player leader = player("Riona");
        Player helper = player("Bran");
        StubPlayerRepository playerRepo = new StubPlayerRepository(leader, helper);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(leader.getUsername());
        roomService.ensurePlayerLocation(helper.getUsername());
        MobRegistry registry = buildRegistry(templateWithHp(100), roomService, playerRepo);

        GameActionResult result = registry.processPlayerAssist(helper, "Riona", ROOM_ID);

        assertEquals("Riona is not fighting anyone.", firstMessage(result));
        assertFalse(registry.isInCombat(helper.getUsername()));
    }

    @Test
    void assist_failsGracefullyWhenTargetsMobAlreadyDead() {
        Player leader = player("Riona");
        Player helper = player("Bran");
        StubPlayerRepository playerRepo = new StubPlayerRepository(leader, helper);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(leader.getUsername());
        roomService.ensurePlayerLocation(helper.getUsername());
        MobRegistry registry = buildRegistry(templateWithHp(100), roomService, playerRepo);

        // Leader engages the mob, then the mob is slain out from under the assister without the
        // combat-target map being cleared, so the stale reference points at a dead instance.
        registry.processPlayerAttack(leader, "Rat", ROOM_ID);
        MobInstance mob = registry.getMobsInRoom(ROOM_ID).get(0);
        mob.takeDamage(mob.currentHp());
        assertFalse(mob.isAlive());

        GameActionResult result = registry.processPlayerAssist(helper, "Riona", ROOM_ID);

        assertEquals("Riona is not fighting anyone.", firstMessage(result));
        assertFalse(registry.isInCombat(helper.getUsername()));
        assertNull(result.updatedSource());
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubMobTemplateRepository(List<MobTemplate> templates) implements MobTemplateRepository {
        @Override public List<MobTemplate> findAll() { return templates; }
    }

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks) implements AttackRepository {
        @Override public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubItemRepository implements ItemRepository {
        @Override public void save(Item item) {}
        @Override public Optional<Item> findById(ItemId id) { return Optional.empty(); }
    }

    private static final class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();
        StubPlayerRepository(Player... players) {
            for (Player player : players) {
                store.put(player.getUsername(), player);
            }
        }
        @Override public void savePlayer(Player player) { store.put(player.getUsername(), player); }
        @Override public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        private final Room room;
        StubRoomRepository(RoomId roomId) {
            this.room = new Room(roomId, "Test Room", "A void.", Map.of(), List.of(), List.of());
        }
        @Override public void save(Room room) {}
        @Override public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
