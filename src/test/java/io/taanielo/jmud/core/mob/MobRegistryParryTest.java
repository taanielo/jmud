package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
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
import io.taanielo.jmud.core.character.AttributeBonus;
import io.taanielo.jmud.core.character.CharacterAttributesResolver;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatAttributeBonusResolver;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.combat.ParryResolver;
import io.taanielo.jmud.core.combat.RangeType;
import io.taanielo.jmud.core.combat.WeaponType;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.player.PlayerVitals;
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
 * Verifies that a player wielding a melee weapon parries a mob's melee swing (taking zero damage) and
 * ripostes the mob, mirroring the PvP parry mechanic on the PvE surface.
 */
class MobRegistryParryTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId MOB_ATTACK_ID = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackId DAGGER_ATTACK_ID = AttackId.of("attack.dagger");
    private static final ItemId DAGGER_ID = ItemId.of("dagger");

    @Test
    void playerParriesMobSwingAndRipostesTheMob() {
        // Mob swing (10 damage) lands then is parried; the player's dagger ripostes for a fixed 7.
        AttackDefinition mobAttack = new AttackDefinition(
            MOB_ATTACK_ID, "bite", 10, 10, 0, 0, 0, List.of(),
            WeaponType.BLUNT, null, RangeType.MELEE, DamageType.PHYSICAL);

        Player target = nimblePlayerWithDagger("hero");

        PlayerEventBus bus = new PlayerEventBus();
        // Rolls: target-select (0), hit (1 <= 75 lands), parry (1 <= 20 parried). The dagger's fixed
        // 7 damage needs no roll (min == max), so a shield/riposte double-roll would exhaust the RNG.
        MobRegistry registry = registryForMobAttack(target, mobAttack, bus, new ScriptedRandom(0, 1, 1));
        registry.setParryResolver(nimbleParryResolver());

        AtomicReference<GameActionResult> received = new AtomicReference<>();
        bus.register(target.getUsername(), received::set);
        registry.tick();

        GameActionResult result = received.get();
        assertNotNull(result, "Expected a game event to be published to the target player");
        // A parried swing deals the player zero damage and leaves their state untouched, so no updated
        // player snapshot is published (only the mob, which is not persisted via the player save, changed).
        assertNull(result.updatedSource(), "A parried swing deals the player zero damage");

        MobInstance mob = registry.getMobsInRoom(ROOM_ID).get(0);
        assertEquals(mob.template().maxHp() - 7, mob.currentHp(), "The riposte should damage the mob for 7");
        assertTrue(containsText(result.messages(), "parry"), "The player should be told they parried");
        assertTrue(containsText(result.messages(), "riposte"), "The riposte should be reported");
    }

    // ── helpers ───────────────────────────────────────────────────────

    private static boolean containsText(List<GameMessage> messages, String needle) {
        String lowered = needle.toLowerCase(Locale.ROOT);
        return messages.stream().anyMatch(m -> m.text().toLowerCase(Locale.ROOT).contains(lowered));
    }

    private ParryResolver nimbleParryResolver() {
        Race nimble = race("nimble", new AttributeBonus(0, 0, 0, 20));
        CombatAttributeBonusResolver attributeResolver = new CombatAttributeBonusResolver(
            CharacterAttributesResolver.fromDefinitions(List.of(nimble), List.of()));
        AttackDefinition dagger = new AttackDefinition(DAGGER_ATTACK_ID, "dagger", 7, 7, 0, 0, 0, List.of());
        AttackRepository attacks = new StubAttackRepository(Map.of(DAGGER_ATTACK_ID, dagger));
        return new ParryResolver(attacks, attributeResolver);
    }

    private Player nimblePlayerWithDagger(String name) {
        Item dagger = Item.builder(DAGGER_ID, "Dagger", "A dagger.", new ItemAttributes(Map.of()))
            .equipSlot(EquipmentSlot.WEAPON).weight(1).value(0).attackRef(DAGGER_ATTACK_ID).build();
        Player base = new Player(
            User.of(Username.of(name), Password.hash("pw", 1)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(),
            "%hp> ",
            false,
            List.of(),
            RaceId.of("nimble"),
            (ClassId) null
        );
        return base
            .withInventory(List.of(dagger))
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.WEAPON, dagger.getId()));
    }

    private Race race(String id, AttributeBonus bonus) {
        return new Race(RaceId.of(id), id, 0, 50, 0, 0, 0, "", bonus);
    }

    private MobRegistry registryForMobAttack(
        Player target, AttackDefinition attack, PlayerEventBus bus, CombatRandom random) {
        MobTemplate template = template("Wolf", 100);
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(MOB_ATTACK_ID, attack));

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());
        PlayerRepository playerRepo = new StubPlayerRepository(target);

        MobRegistry registry = new MobRegistry(
            templateRepo, new StubItemRepository(Map.of()), attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, random);
        registry.init();
        return registry;
    }

    private MobTemplate template(String name, int maxHp) {
        return new MobTemplate(
            MobId.of("mob." + name.toLowerCase(Locale.ROOT)),
            name,
            maxHp,
            MOB_ATTACK_ID,
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
