package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.combat.EquipmentResistanceResolver;
import io.taanielo.jmud.core.combat.RangeType;
import io.taanielo.jmud.core.combat.WeaponType;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Verifies that the elemental-resistance mitigation applies on the mob-attacks-player path in
 * {@link MobRegistry}: a fire attack is reduced by the defender's fire_resist gear, while a
 * physical attack is unaffected by resistance gear.
 */
class MobRegistryResistanceTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId ATTACK_ID = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final ItemId WARD_ID = ItemId.of("fire-ward");

    @Test
    void fireAttackIsReducedByEquippedFireResistance() {
        // 10 damage, 50% fire_resist -> 5 damage. Player starts at 20 HP -> 15 remaining.
        Player updated = tickAndCapture(DamageType.FIRE, 50);
        assertEquals(15, updated.getVitals().hp());
    }

    @Test
    void physicalAttackIgnoresResistanceGear() {
        // Physical damage is never resisted even with fire_resist gear: full 10 damage -> 10 HP.
        Player updated = tickAndCapture(DamageType.PHYSICAL, 50);
        assertEquals(10, updated.getVitals().hp());
    }

    private Player tickAndCapture(DamageType damageType, int resistPercent) {
        Item ward = Item.builder(WARD_ID, "Fire Ward", "A fire ward.",
                new ItemAttributes(Map.of("fire_resist", resistPercent)))
            .equipSlot(EquipmentSlot.NECK)
            .weight(1)
            .value(0)
            .build();
        Player target = player("hero")
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.NECK, WARD_ID));

        AttackDefinition attack = new AttackDefinition(
            ATTACK_ID, "breath", 10, 10, 0, 0, 0, List.of(),
            WeaponType.BLUNT, null, RangeType.MELEE, damageType);

        PlayerEventBus bus = new PlayerEventBus();
        MobRegistry registry = buildRegistry(target, attack, ward, bus);

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);

        registry.tick();

        GameActionResult result = received.get();
        assertNotNull(result, "Expected a game event to be published to the target player");
        Player updatedSource = result.updatedSource();
        assertNotNull(updatedSource, "Expected the damaged player snapshot to be published");
        return updatedSource;
    }

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobRegistry buildRegistry(Player target, AttackDefinition attack, Item ward, PlayerEventBus bus) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.wyrm"),
            "Ember Wyrm",
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
        ItemRepository wardRepo = new StubItemRepository(Map.of(WARD_ID, ward));

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());

        PlayerRepository playerRepo = new StubPlayerRepository(target);

        MobRegistry registry = new MobRegistry(
            templateRepo, new StubItemRepository(Map.of()), attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, MobRegistryTestSupport.random());
        registry.setEquipmentResistanceResolver(new EquipmentResistanceResolver(wardRepo));
        registry.init();
        return registry;
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
        public void save(Item item) {
        }

        @Override
        public Optional<Item> findById(ItemId id) {
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

        StubRoomRepository(RoomId roomId) {
            this.room = new Room(
                roomId, "Test Room", "A featureless void.", Map.of(), List.of(), List.of());
        }

        @Override
        public void save(Room room) {
        }

        @Override
        public Optional<Room> findById(RoomId id) {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
