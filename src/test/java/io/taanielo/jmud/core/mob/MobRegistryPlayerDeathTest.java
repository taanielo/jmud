package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import io.taanielo.jmud.core.player.DeathSettings;
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
 * Verifies that a player felled by a mob's own AI-driven attack receives the same complete death
 * message as every other death path (issue #805): the "You have died." line, the real respawn-room
 * display name (never the raw kebab-case id), and newbie-grace handling — all delegated to the shared
 * {@link io.taanielo.jmud.core.action.PlayerDeathService} rather than a divergent copy.
 */
class MobRegistryPlayerDeathTest {

    // The mob's room is also the default respawn room, so the death message can resolve a real
    // display name ("Training Yard") for the "You will awaken in ..." line.
    private static final RoomId ROOM_ID = RoomId.of(DeathSettings.RESPAWN_ROOM_ID);
    private static final String ROOM_NAME = "Training Yard";
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "bite", 1, 1, 0, 0, 0, List.of());

    private Player oneHpPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ");
        // Reduce to a single hit point so the mob's 1-damage bite is lethal on the engaging tick.
        return player.withVitals(player.getVitals().damage(player.getVitals().hp() - 1));
    }

    private MobRegistry buildRegistryWithAggressiveMob(Player target, PlayerEventBus bus) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.wolf"),
            "Dire Wolf",
            100,
            DEFAULT_ATTACK,
            null,
            true,   // aggressive
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

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID, ROOM_NAME), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(target);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    @Test
    void mobKillProducesSharedDeathMessage() {
        Player hero = oneHpPlayer("hero");
        PlayerEventBus bus = new PlayerEventBus();
        List<String> delivered = new ArrayList<>();
        bus.register(hero.getUsername(),
            result -> result.messages().forEach(message -> delivered.add(message.text())));

        MobRegistry registry = buildRegistryWithAggressiveMob(hero, bus);
        registry.tick();

        assertTrue(delivered.contains("You have died."),
            "A mob-initiated kill must announce the death like every other path, got: " + delivered);
        assertTrue(delivered.contains("You will awaken in " + ROOM_NAME + "."),
            "The respawn room must be named by its display name, not a raw id, got: " + delivered);
        assertFalse(delivered.stream().anyMatch(line -> line.contains(DeathSettings.RESPAWN_ROOM_ID)),
            "The death message must never leak the raw room id, got: " + delivered);
        // A level-1 victim is newbie-grace protected under the default grace level, so they are told
        // they keep their belongings rather than being left with no clue about a corpse.
        assertTrue(delivered.stream().anyMatch(line -> line.contains("newbie grace")),
            "A newbie victim must be told about the grace that spares their belongings, got: " + delivered);
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubMobTemplateRepository(List<MobTemplate> templates)
        implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() {
            return templates;
        }
    }

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks)
        implements AttackRepository {
        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubItemRepository implements ItemRepository {
        private final Map<ItemId, Item> items;

        StubItemRepository(Map<ItemId, Item> items) {
            this.items = Map.copyOf(items);
        }

        @Override
        public void save(Item item) throws RepositoryException {
        }

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.ofNullable(items.get(id));
        }
    }

    private static final class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();

        StubPlayerRepository(Player initial) {
            store.put(initial.getUsername(), initial);
        }

        @Override
        public void savePlayer(Player player) {
            store.put(player.getUsername(), player);
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        private final Room room;

        StubRoomRepository(RoomId roomId, String name) {
            this.room = new Room(
                roomId, name, "A sandy practice ground.", Map.of(), List.of(), List.of());
        }

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
