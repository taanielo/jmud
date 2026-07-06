package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
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
 * Unit tests for {@link MobRegistry#fleeCombat(Username)} and
 * {@link MobRegistry#isInCombat(Username)}.
 */
class MobRegistryFleeCombatTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK =
        AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobRegistry buildRegistryWithMob(Player attacker, int mobHp) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            mobHp,
            null,
            null,
            false,
            List.of(),
            ROOM_ID,
            1,
            10,
            5,
            null,
            null,
            false
        );
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(attacker.getUsername());

        PlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus);
        registry.init();
        return registry;
    }

    @Test
    void isInCombat_returnsFalse_whenPlayerHasNotAttacked() {
        Player attacker = player("hero");
        MobRegistry registry = buildRegistryWithMob(attacker, 100);

        assertFalse(registry.isInCombat(attacker.getUsername()),
            "Player should not be in combat before attacking");
    }

    @Test
    void isInCombat_returnsTrue_afterPlayerAttacks() {
        Player attacker = player("hero");
        MobRegistry registry = buildRegistryWithMob(attacker, 100);

        registry.processPlayerAttack(attacker, "Goblin", ROOM_ID);

        assertTrue(registry.isInCombat(attacker.getUsername()),
            "Player should be in combat after attacking a mob");
    }

    @Test
    void fleeCombat_clearsPlayerCombatTarget() {
        Player attacker = player("hero");
        MobRegistry registry = buildRegistryWithMob(attacker, 100);

        registry.processPlayerAttack(attacker, "Goblin", ROOM_ID);
        assertTrue(registry.isInCombat(attacker.getUsername()),
            "Precondition: player must be in combat");

        registry.fleeCombat(attacker.getUsername());

        assertFalse(registry.isInCombat(attacker.getUsername()),
            "Player should not be in combat after fleeing");
    }

    @Test
    void fleeCombat_isIdempotent_whenPlayerNotInCombat() {
        Player attacker = player("hero");
        MobRegistry registry = buildRegistryWithMob(attacker, 100);

        // Should not throw even when player was never in combat.
        registry.fleeCombat(attacker.getUsername());

        assertFalse(registry.isInCombat(attacker.getUsername()),
            "Player should still not be in combat after calling flee when not in combat");
    }

    @Test
    void fleeCombat_disengagesMobFromPlayer() {
        Player attacker = player("hero");
        MobRegistry registry = buildRegistryWithMob(attacker, 100);

        registry.processPlayerAttack(attacker, "Goblin", ROOM_ID);
        registry.fleeCombat(attacker.getUsername());

        // After fleeing, the mob should no longer have the player in its engaged set.
        // We verify indirectly: isInCombat is false, meaning the registry is consistent.
        assertFalse(registry.isInCombat(attacker.getUsername()),
            "Player combat state must be cleared after fleeing");
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
