package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.taanielo.jmud.core.combat.AttackEffectApplication;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.WeaponType;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectStacking;
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
 * Verifies that a mob attack configured with {@link AttackDefinition#effectOnHit()}
 * applies the status effect to the player it hits, reachable via {@link MobRegistry}'s
 * mob AI tick — the path used by mob-vs-player combat in-game.
 */
class MobRegistryOnHitEffectTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId ATTACK_ID = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final EffectId POISON_ID = EffectId.of("poison");

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobRegistry buildRegistry(
        Player target,
        AttackDefinition attack,
        EffectEngine effectEngine,
        PlayerEventBus bus
    ) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.spider"),
            "Giant Spider",
            100,
            ATTACK_ID,
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
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(ATTACK_ID, attack));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());

        PlayerRepository playerRepo = new StubPlayerRepository(target);

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus);
        registry.setEffectEngine(effectEngine);
        registry.init();
        return registry;
    }

    @Test
    void mobAttackAppliesConfiguredEffectOnHit() {
        Player target = player("hero");
        EffectDefinition poison = new EffectDefinition(
            POISON_ID, "Poison", 10, 1, EffectStacking.REFRESH, List.of(), null
        );
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of(POISON_ID, poison)));
        AttackDefinition attack = new AttackDefinition(
            ATTACK_ID, "bite", 1, 1, 0, 0, 0, List.of(), WeaponType.PIERCING,
            new AttackEffectApplication(POISON_ID, 100)
        );
        PlayerEventBus bus = new PlayerEventBus();
        MobRegistry registry = buildRegistry(target, attack, effectEngine, bus);

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);

        registry.tick();

        GameActionResult result = received.get();
        assertNotNull(result, "Expected a game event to be published to the target player");
        Player updatedSource = result.updatedSource();
        assertNotNull(updatedSource, "Expected the damaged player snapshot to be published");
        assertEquals(1, updatedSource.effects().size());
        assertEquals(POISON_ID, updatedSource.effects().getFirst().id());
    }

    @Test
    void mobAttackDoesNotApplyEffectWithoutEffectEngine() {
        Player target = player("hero2");
        AttackDefinition attack = new AttackDefinition(
            ATTACK_ID, "bite", 1, 1, 0, 0, 0, List.of(), WeaponType.PIERCING,
            new AttackEffectApplication(POISON_ID, 100)
        );
        PlayerEventBus bus = new PlayerEventBus();
        MobRegistry registry = buildRegistry(target, attack, null, bus);

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);

        registry.tick();

        GameActionResult result = received.get();
        assertNotNull(result, "Expected a game event to be published to the target player");
        Player updatedSource = result.updatedSource();
        assertNotNull(updatedSource);
        assertTrue(updatedSource.effects().isEmpty());
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

    private record StubEffectRepository(Map<EffectId, EffectDefinition> definitions)
        implements EffectRepository {
        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            return Optional.ofNullable(definitions.get(id));
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
