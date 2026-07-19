package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
 * Verifies mob-side parry: a defensively-trained mob (authored {@code parry_chance}) fully parries an
 * otherwise-landing player melee swing (dealing the player zero damage) and ripostes the attacker,
 * while ordinary mobs are unaffected and non-melee attacks (a ranged shot) never roll a parry.
 */
class MobRegistryMobParryTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");

    private static final AttackId UNARMED = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition UNARMED_MELEE =
        new AttackDefinition(UNARMED, "punch", 6, 6, 0, 0, 0, List.of());

    private static final AttackId GUARD_ATTACK = AttackId.of("attack.guard");
    private static final AttackDefinition GUARD_MELEE = new AttackDefinition(
        GUARD_ATTACK, "halberd", 8, 8, 0, 0, 0, List.of(), WeaponType.SLASHING, null, RangeType.MELEE);

    private static final AttackId BOW_ATTACK = AttackId.of("attack.long-bow");
    private static final AttackDefinition BOW_RANGED = new AttackDefinition(
        BOW_ATTACK, "long bow", 3, 3, 0, 0, 0, List.of(), WeaponType.PIERCING, null, RangeType.RANGED);

    private static final Map<AttackId, AttackDefinition> ATTACKS =
        Map.of(UNARMED, UNARMED_MELEE, GUARD_ATTACK, GUARD_MELEE, BOW_ATTACK, BOW_RANGED);

    // ── melee: a parry-capable mob parries the player's swing and ripostes ──

    @Test
    void parryCapableMobNegatesPlayerMeleeDamageAndRiposts() {
        Player attacker = player("hero");
        // Rolls: hit (10 <= 75 lands), crit (50 > 5 no crit), parry (1 <= 25 parries). The player's
        // 6==6 and the guard's 8==8 damage need no rolls, so exactly three rolls are consumed.
        MobRegistry registry = registry(attacker, guard(100, 25, ROOM_A), ROOM_A, new ScriptedRandom(10, 50, 1));

        GameActionResult result = registry.processPlayerAttack(attacker, "guard", ROOM_A);

        MobInstance mob = registry.getMobsInRoom(ROOM_A).get(0);
        assertEquals(mob.template().maxHp(), mob.currentHp(),
            "A parried swing deals the mob zero damage");
        Player updated = result.updatedSource();
        assertNotNull(updated, "The riposte-damaged player snapshot should be published");
        assertEquals(attacker.getVitals().hp() - 8, updated.getVitals().hp(),
            "The mob's riposte should damage the attacker for its fixed 8");
        assertTrue(containsText(result.messages(), "parr"),
            "The attacker should be told the mob parried");
        assertTrue(containsText(result.messages(), "riposte"),
            "The riposte should be reported to the attacker");
    }

    // ── melee regression: an ordinary mob's is unaffected ──

    @Test
    void nonParryingMobTakesFullMeleeDamageAndNeverRiposts() {
        Player attacker = player("hero");
        // Rolls: hit (10 <= 75 lands), crit (50 > 5 no crit). A mob with no parry chance never rolls a
        // parry, so no third roll is consumed and the RNG stream is identical to the pre-feature path.
        MobRegistry registry = registry(attacker, ordinaryRat(100), ROOM_A, new ScriptedRandom(10, 50));

        GameActionResult result = registry.processPlayerAttack(attacker, "rat", ROOM_A);

        MobInstance mob = registry.getMobsInRoom(ROOM_A).get(0);
        assertEquals(mob.template().maxHp() - 6, mob.currentHp(),
            "An ordinary mob takes the player's full 6 melee damage");
        assertNull(result.updatedSource(),
            "An ordinary mob never ripostes, so the attacker is untouched");
        assertTrue(containsText(result.messages(), "strike"),
            "A normal landed hit reads as a strike, not a parry");
        assertFalse(containsText(result.messages(), "parr"),
            "An ordinary mob's hit must not report a parry");
    }

    // ── non-melee: a ranged shot at a parry-capable mob never rolls a parry ──

    @Test
    void rangedShotAtParryCapableMobIsNeverParried() {
        Player shooter = withBow(player("robin"));
        // Rolls: hit (10 <= 75 lands), crit (50 > 5 no crit). No parry roll — the SHOOT path is
        // melee-excluded, so the parry-capable mob still takes the bow's fixed 3 damage.
        MobRegistry registry = registry(shooter, guard(100, 25, ROOM_B), ROOM_A, new ScriptedRandom(10, 50));

        GameActionResult result =
            registry.processPlayerRangedAttack(shooter, "guard", Direction.NORTH, ROOM_A);

        MobInstance mob = registry.getMobsInRoom(ROOM_A).get(0);
        assertEquals(mob.template().maxHp() - 3, mob.currentHp(),
            "A ranged shot at a parry-capable mob still lands its damage — ranged never rolls a parry");
        assertTrue(containsText(result.messages(), "fire at"),
            "The shooter should see the ranged hit line");
        assertFalse(containsText(result.messages(), "parr"),
            "A ranged shot must never be parried");
    }

    // ── helpers ───────────────────────────────────────────────────────

    private static boolean containsText(List<GameMessage> messages, String needle) {
        String lowered = needle.toLowerCase(Locale.ROOT);
        return messages.stream().anyMatch(m -> m.text().toLowerCase(Locale.ROOT).contains(lowered));
    }

    private Player player(String name) {
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

    private MobTemplate guard(int maxHp, int parryChancePercent, RoomId spawnRoom) {
        return new MobTemplate(
            MobId.of("mob.guard"), "Guard", maxHp, GUARD_ATTACK, null, false,
            List.of(), spawnRoom, 1, 10, 5, null, List.of(), false, null, null, false,
            null, null, false, false, parryChancePercent);
    }

    private MobTemplate ordinaryRat(int maxHp) {
        return new MobTemplate(
            MobId.of("mob.rat"), "Rat", maxHp, GUARD_ATTACK, null, false,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false);
    }

    private MobRegistry registry(Player player, MobTemplate template, RoomId playerRoom, CombatRandom random) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(ATTACKS);
        RoomService roomService = new RoomService(new StubRoomRepository(), playerRoom);
        roomService.ensurePlayerLocation(player.getUsername());
        StubPlayerRepository playerRepo = new StubPlayerRepository(player);
        MobRegistry registry = new MobRegistry(
            templateRepo, new StubItemRepository(), attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), new PlayerEventBus(), random);
        registry.init();
        return registry;
    }

    // ── scripted RNG ──────────────────────────────────────────────────

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
        public void save(Item item) {
        }

        @Override
        public Optional<Item> findById(ItemId id) {
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
        public void save(Room room) {
        }

        @Override
        public Optional<Room> findById(RoomId id) {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
