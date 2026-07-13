package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
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
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.RangeType;
import io.taanielo.jmud.core.combat.WeaponType;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Direction;
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
 * Unit tests for the {@code SHOOT} command's cross-room ranged-attack resolution in
 * {@link MobRegistry#processPlayerRangedAttack}.
 */
class MobRegistryRangedAttackTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");

    private static final AttackId UNARMED = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition UNARMED_MELEE =
        new AttackDefinition(UNARMED, "punch", 1, 1, 0, 0, 0, List.of());

    private static final AttackId BOW_ATTACK = AttackId.of("attack.long-bow");
    private static final AttackDefinition BOW_RANGED = new AttackDefinition(
        BOW_ATTACK, "long bow", 3, 3, 0, 0, 0, List.of(), WeaponType.PIERCING, null, RangeType.RANGED);

    private static final Map<AttackId, AttackDefinition> ATTACKS =
        Map.of(UNARMED, UNARMED_MELEE, BOW_ATTACK, BOW_RANGED);

    private Player archer(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private Player withBow(Player player) {
        Item bow = Item.builder(
                ItemId.of("long-bow"), "Long Bow", "A tall yew bow.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .attackRef(BOW_ATTACK)
            .weight(4)
            .value(40)
            .build();
        return player.addItem(bow)
            .withEquipment(player.getEquipment().equip(EquipmentSlot.WEAPON, bow.getId()));
    }

    private MobRegistry buildRegistry(Player shooter, int mobHp) {
        return buildRegistry(shooter, mobHp, MobRegistryTestSupport.random(), ATTACKS);
    }

    private MobRegistry buildRegistry(
        Player shooter, int mobHp, CombatRandom random, Map<AttackId, AttackDefinition> attacks) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            mobHp,
            UNARMED,
            null,
            false,
            List.of(),
            ROOM_B,
            1,
            10,
            5,
            null,
            null,
            false
        );
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(attacks);
        ItemRepository itemRepo = new StubItemRepository();

        RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_A);
        roomService.ensurePlayerLocation(shooter.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(shooter);
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, random);
        registry.init();
        return registry;
    }

    private MobInstance goblin(MobRegistry registry, RoomId roomId) {
        return registry.getMobsInRoom(roomId).stream()
            .filter(m -> m.template().name().equals("Goblin"))
            .findFirst()
            .orElseThrow();
    }

    private static boolean containsText(GameActionResult result, String fragment) {
        return result.messages().stream().anyMatch(m -> m.text().contains(fragment));
    }

    @Test
    void rangedAttack_damagesMobInAdjacentRoom() {
        Player shooter = withBow(archer("robin"));
        MobRegistry registry = buildRegistry(shooter, 100);

        GameActionResult result =
            registry.processPlayerRangedAttack(shooter, "goblin", Direction.NORTH, ROOM_A);

        assertEquals(97, goblin(registry, ROOM_A).currentHp(),
            "Mob should take the bow's fixed 3 damage; but it survives and moves into ROOM_A");
        assertTrue(containsText(result, "You fire at the Goblin"),
            "Shooter should be told they fired at the mob");
    }

    @Test
    void rangedAttack_missingTarget_returnsError() {
        Player shooter = withBow(archer("robin"));
        MobRegistry registry = buildRegistry(shooter, 100);

        GameActionResult result =
            registry.processPlayerRangedAttack(shooter, "dragon", Direction.NORTH, ROOM_A);

        assertTrue(containsText(result, "You don't see dragon"),
            "Shooting a mob that is not in the adjacent room should fail gracefully");
    }

    @Test
    void rangedAttack_withoutRangedWeapon_returnsError() {
        Player unarmed = archer("robin");
        MobRegistry registry = buildRegistry(unarmed, 100);

        GameActionResult result =
            registry.processPlayerRangedAttack(unarmed, "goblin", Direction.NORTH, ROOM_A);

        assertTrue(containsText(result, "not wielding a ranged weapon"),
            "An unarmed (non-ranged) player cannot shoot");
        assertEquals(100, goblin(registry, ROOM_B).currentHp(),
            "The mob should be untouched when the shot is rejected");
    }

    @Test
    void rangedAttack_noExitInDirection_returnsError() {
        Player shooter = withBow(archer("robin"));
        MobRegistry registry = buildRegistry(shooter, 100);

        GameActionResult result =
            registry.processPlayerRangedAttack(shooter, "goblin", Direction.EAST, ROOM_A);

        assertTrue(containsText(result, "no exit to the east"),
            "Shooting in a direction with no exit should fail gracefully");
    }

    @Test
    void rangedAttack_onHit_mobClosesDistanceAndEngages() {
        Player shooter = withBow(archer("robin"));
        MobRegistry registry = buildRegistry(shooter, 100);

        assertFalse(registry.isInCombat(shooter.getUsername()),
            "Precondition: the shooter is not yet in combat");

        registry.processPlayerRangedAttack(shooter, "goblin", Direction.NORTH, ROOM_A);

        assertEquals(ROOM_A, goblin(registry, ROOM_A).roomId(),
            "A surviving ranged-attacked mob should close the distance into the shooter's room");
        assertTrue(registry.isInCombat(shooter.getUsername()),
            "The retaliating mob should engage the shooter");
    }

    @Test
    void rangedAttack_slaysWeakMob_awardsKillRewards() {
        Player shooter = withBow(archer("robin"));
        MobRegistry registry = buildRegistry(shooter, 3);

        GameActionResult result =
            registry.processPlayerRangedAttack(shooter, "goblin", Direction.NORTH, ROOM_A);

        assertTrue(containsText(result, "You slay the Goblin"),
            "A mob dropped to zero HP by a ranged attack should be slain");
        assertFalse(registry.isInCombat(shooter.getUsername()),
            "A slain mob does not engage the shooter");
    }

    @Test
    void rangedAttack_lowAccuracyShooterMisses_dealsNoDamageAndMobStaysPut() {
        // hit_bonus of -100 floors the bow's hit chance to MIN_HIT_CHANCE (5); a roll of 50 misses.
        AttackDefinition weakBow = new AttackDefinition(
            BOW_ATTACK, "long bow", 3, 3, -100, 0, 0, List.of(),
            WeaponType.PIERCING, null, RangeType.RANGED);
        Player shooter = withBow(archer("robin"));
        MobRegistry registry = buildRegistry(
            shooter, 100, new ScriptedRandom(50), Map.of(UNARMED, UNARMED_MELEE, BOW_ATTACK, weakBow));

        GameActionResult result =
            registry.processPlayerRangedAttack(shooter, "goblin", Direction.NORTH, ROOM_A);

        assertEquals(100, goblin(registry, ROOM_B).currentHp(),
            "A missed shot must deal no damage; the mob stays in the adjacent room");
        assertTrue(containsText(result, "sails wide"),
            "The shooter should see a miss message, not a hit line");
        assertFalse(containsText(result, "You fire at the Goblin"),
            "A miss must not report a hit for damage");
        assertFalse(registry.isInCombat(shooter.getUsername()),
            "A missed shot gives the mob no reason to close the distance or engage");
    }

    @Test
    void rangedAttack_critLandsBonusDamage() {
        // crit_bonus of 95 lifts crit chance to 100%; a fixed 6-damage hit crits for 6 * multiplier.
        AttackDefinition critBow = new AttackDefinition(
            BOW_ATTACK, "long bow", 6, 6, 0, 95, 0, List.of(),
            WeaponType.PIERCING, null, RangeType.RANGED);
        Player shooter = withBow(archer("robin"));
        // Rolls: hit (10 <= 75 lands), crit (1 <= 100 crits).
        MobRegistry registry = buildRegistry(
            shooter, 100, new ScriptedRandom(10, 1), Map.of(UNARMED, UNARMED_MELEE, BOW_ATTACK, critBow));

        GameActionResult result =
            registry.processPlayerRangedAttack(shooter, "goblin", Direction.NORTH, ROOM_A);

        int expectedDamage = 6 * CombatSettings.critMultiplier();
        assertEquals(100 - expectedDamage, goblin(registry, ROOM_A).currentHp(),
            "A critical shot should deal bonus damage equal to the crit multiplier");
        assertTrue(containsText(result, "critical"),
            "A crit should read distinctly from a normal hit");
    }

    @Test
    void rangedAttack_normalHit_stillTriggersMobCharge() {
        // Rolls: hit (10 <= 75 lands), crit (50 > 5 does not crit).
        Player shooter = withBow(archer("robin"));
        MobRegistry registry = buildRegistry(shooter, 100, new ScriptedRandom(10, 50), ATTACKS);

        GameActionResult result =
            registry.processPlayerRangedAttack(shooter, "goblin", Direction.NORTH, ROOM_A);

        assertEquals(97, goblin(registry, ROOM_A).currentHp(),
            "A normal landed shot deals the bow's fixed 3 damage");
        assertFalse(containsText(result, "critical"),
            "A normal hit is not reported as a crit");
        assertEquals(ROOM_A, goblin(registry, ROOM_A).roomId(),
            "A surviving mob hit by a landed shot closes the distance into the shooter's room");
        assertTrue(registry.isInCombat(shooter.getUsername()),
            "A landed shot makes the retaliating mob engage the shooter");
    }

    // ── scripted RNG ──────────────────────────────────────────────────

    /**
     * A {@link CombatRandom} returning a fixed sequence of rolls (each clamped into the requested
     * range) so ranged combat outcomes are fully deterministic. {@link #nextDouble()} returns a
     * fixed {@code 1.0} so it never consumes a scripted roll.
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
        @Override
        public void save(Item item) throws RepositoryException {
        }

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.empty();
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
        private final Map<RoomId, Room> rooms = Map.of(
            ROOM_A, new Room(
                ROOM_A, "Room A", "A clearing.", Map.of(Direction.NORTH, ROOM_B), List.of(), List.of()),
            ROOM_B, new Room(
                ROOM_B, "Room B", "A thicket.", Map.of(Direction.SOUTH, ROOM_A), List.of(), List.of())
        );

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
