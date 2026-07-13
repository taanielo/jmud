package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
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
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.RangeType;
import io.taanielo.jmud.core.combat.ShieldBlockResolver;
import io.taanielo.jmud.core.combat.WeaponType;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageSpec;
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
 * Verifies that player-vs-mob melee combat shares the PvP hit/crit/block resolution: a player can
 * miss or crit a mob, a mob can miss a heavily-armoured player and be blocked by a shield — all
 * driven through a scripted {@link CombatRandom} so every outcome is deterministic.
 */
class MobRegistryHitResolutionTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId ATTACK_ID = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final ItemId ARMOUR_ID = ItemId.of("plate-chest");
    private static final ItemId SHIELD_ID = ItemId.of("tower-shield");

    // ── player attacks a mob ──────────────────────────────────────────

    @Test
    void lowAccuracyPlayerMissesMobAtFlooredHitChance() {
        // hit_bonus of -100 floors the hit chance to MIN_HIT_CHANCE (5); a roll of 50 misses.
        AttackDefinition attack = attack(-100, 0, 6, 6, List.of());
        MobRegistry registry = registryWithMob(attack, new ScriptedRandom(50));

        Player attacker = player("hero");
        GameActionResult result = registry.processPlayerAttack(attacker, "goblin", ROOM_ID);

        MobInstance mob = registry.getMobsInRoom(ROOM_ID).get(0);
        assertEquals(mob.template().maxHp(), mob.currentHp(), "A missed swing must deal no damage");
        assertTrue(containsText(result.messages(), "miss"),
            "The attacker should be told they missed");
    }

    @Test
    void playerCritLandsBonusDamageOnMob() {
        // crit_bonus of 95 lifts the crit chance to 100%; a fixed 6-damage hit crits for 6 * 2 = 12.
        AttackDefinition attack = attack(0, 95, 6, 6, List.of());
        MobRegistry registry = registryWithMob(attack, new ScriptedRandom(10, 1));

        Player attacker = player("hero");
        GameActionResult result = registry.processPlayerAttack(attacker, "goblin", ROOM_ID);

        MobInstance mob = registry.getMobsInRoom(ROOM_ID).get(0);
        int expectedDamage = 6 * CombatSettings.critMultiplier();
        assertEquals(mob.template().maxHp() - expectedDamage, mob.currentHp(),
            "A crit should deal bonus damage equal to the crit multiplier");
        assertTrue(containsText(result.messages(), "critical"),
            "A crit should read distinctly from a normal hit");
    }

    // ── mob attacks a player ──────────────────────────────────────────

    @Test
    void mobMissesHeavilyArmouredPlayer() {
        // AC 100 floors the mob's hit chance to MIN_HIT_CHANCE (5); a hit roll of 100 misses.
        AttackDefinition attack = attack(0, 0, 10, 10, List.of(
            new MessageSpec(MessagePhase.ATTACK_MISS, MessageChannel.SELF,
                "The wolf snaps at you but misses.")));
        Item armour = Item.builder(ARMOUR_ID, "Plate Chest", "Heavy plate.",
                new ItemAttributes(Map.of("ac", 100)))
            .equipSlot(EquipmentSlot.CHEST)
            .weight(1)
            .value(0)
            .build();
        Player target = player("hero")
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.CHEST, ARMOUR_ID));
        ItemRepository armourRepo = new StubItemRepository(Map.of(ARMOUR_ID, armour));

        PlayerEventBus bus = new PlayerEventBus();
        MobRegistry registry = registryForMobAttack(target, attack, bus, new ScriptedRandom(0, 100));
        registry.setEquipmentArmorResolver(new EquipmentArmorResolver(armourRepo));

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);
        registry.tick();

        GameActionResult result = received.get();
        assertNotNull(result, "Expected a game event to be published to the target player");
        assertTrue(containsText(result.messages(), "misses"),
            "The authored ATTACK_MISS line should render on a mob miss");
    }

    @Test
    void shieldBlockReducesMobDamage() {
        // A landing 10-damage hit is blocked at 100% chance for 50% reduction -> 5 damage.
        AttackDefinition attack = attack(0, 0, 10, 10, List.of(
            new MessageSpec(MessagePhase.ATTACK_BLOCK, MessageChannel.SELF,
                "You raise your shield and block the blow.")));
        Item shield = Item.builder(SHIELD_ID, "Tower Shield", "A broad shield.",
                new ItemAttributes(Map.of("block_chance", 100, "block_reduction", 50)))
            .equipSlot(EquipmentSlot.OFFHAND)
            .weight(1)
            .value(0)
            .build();
        Player target = player("hero")
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.OFFHAND, SHIELD_ID));
        ItemRepository shieldRepo = new StubItemRepository(Map.of(SHIELD_ID, shield));

        PlayerEventBus bus = new PlayerEventBus();
        // Rolls: target-select (0), hit (1 <= 75), block (1 <= 100). min == max means no damage roll.
        MobRegistry registry = registryForMobAttack(target, attack, bus, new ScriptedRandom(0, 1, 1));
        registry.setShieldBlockResolver(new ShieldBlockResolver(shieldRepo));

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);
        registry.tick();

        GameActionResult result = received.get();
        assertNotNull(result, "Expected a game event to be published to the target player");
        Player updated = result.updatedSource();
        assertNotNull(updated, "Expected the damaged player snapshot to be published");
        // 20 starting HP - 5 blocked damage = 15.
        assertEquals(15, updated.getVitals().hp(), "A block should halve the incoming 10 damage");
        assertTrue(containsText(result.messages(), "block"),
            "The authored ATTACK_BLOCK line should render on a blocked hit");
    }

    // ── helpers ───────────────────────────────────────────────────────

    private static boolean containsText(List<GameMessage> messages, String needle) {
        String lowered = needle.toLowerCase();
        return messages.stream().anyMatch(m -> m.text().toLowerCase().contains(lowered));
    }

    private AttackDefinition attack(
        int hitBonus, int critBonus, int minDamage, int maxDamage, List<MessageSpec> messages) {
        return new AttackDefinition(
            ATTACK_ID, "strike", minDamage, maxDamage, hitBonus, critBonus, 0, messages,
            WeaponType.BLUNT, null, RangeType.MELEE, DamageType.PHYSICAL);
    }

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobRegistry registryWithMob(AttackDefinition attack, CombatRandom random) {
        MobTemplate template = template("Goblin", 100, true);
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(ATTACK_ID, attack));
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);

        PlayerRepository playerRepo = new StubPlayerRepository(player("hero"));
        MobRegistry registry = new MobRegistry(
            templateRepo, new StubItemRepository(Map.of()), attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), new PlayerEventBus(), random);
        registry.init();
        return registry;
    }

    private MobRegistry registryForMobAttack(
        Player target, AttackDefinition attack, PlayerEventBus bus, CombatRandom random) {
        MobTemplate template = template("Wolf", 100, true);
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(ATTACK_ID, attack));

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());
        PlayerRepository playerRepo = new StubPlayerRepository(target);

        MobRegistry registry = new MobRegistry(
            templateRepo, new StubItemRepository(Map.of()), attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, random);
        registry.init();
        return registry;
    }

    private MobTemplate template(String name, int maxHp, boolean aggressive) {
        return new MobTemplate(
            MobId.of("mob." + name.toLowerCase()),
            name,
            maxHp,
            ATTACK_ID,
            null,
            aggressive,
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

    // ── scripted RNG ──────────────────────────────────────────────────

    /**
     * A {@link CombatRandom} that returns a fixed sequence of rolls, each clamped into the requested
     * range, so combat outcomes are fully deterministic. {@link #nextDouble()} is overridden to a
     * fixed value so it never consumes a scripted roll (loot/wander rolls in the tested paths are
     * suppressed by the test data instead).
     */
    private static final class ScriptedRandom implements CombatRandom {
        private final Deque<Integer> rolls = new ArrayDeque<>();

        ScriptedRandom(int... values) {
            for (int value : values) {
                rolls.add(value);
            }
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            Integer next = rolls.poll();
            int value = next == null ? minInclusive : next;
            return Math.max(minInclusive, Math.min(maxInclusive, value));
        }

        @Override
        public double nextDouble() {
            return 1.0;
        }
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
