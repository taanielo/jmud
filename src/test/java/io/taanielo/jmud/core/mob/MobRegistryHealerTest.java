package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Verifies healer mob AI (issue #733): a healer-tagged mob mends its most-wounded ally instead of
 * attacking, never overheals past the ally's max HP, attacks normally when no ally needs healing, and
 * does not also heal on a telegraph or flee decision.
 */
class MobRegistryHealerTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");

    private static final AttackId HEALER_ATTACK = AttackId.of("attack.medic");
    private static final AttackId ALLY_ATTACK = AttackId.of("attack.ally");
    private static final AttackDefinition HEALER_MELEE =
        new AttackDefinition(HEALER_ATTACK, "cudgel", 3, 3, 0, 0, 0, List.of());
    private static final AttackDefinition ALLY_MELEE =
        new AttackDefinition(ALLY_ATTACK, "blade", 2, 2, 0, 0, 0, List.of());
    private static final Map<AttackId, AttackDefinition> ATTACKS =
        Map.of(HEALER_ATTACK, HEALER_MELEE, ALLY_ATTACK, ALLY_MELEE);

    private static final int HEAL_AMOUNT = 12;

    @Test
    void healerMendsWoundedAllyInsteadOfAttacking() {
        Harness h = new Harness();
        MobInstance ally = h.ally();
        ally.takeDamage(30); // 50 -> 20, below the 50% (25) threshold

        h.registry.tick();

        assertEquals(20 + HEAL_AMOUNT, ally.currentHp(),
            "The healer should restore its heal roll to the wounded ally");
        assertTrue(h.contains("chants over"),
            "The room should see the healer mending its ally");
        assertEquals(h.startingPlayerHp(), h.playerHp(),
            "A healer that heals this decision does not also attack the player");
    }

    @Test
    void healDoesNotOverhealPastMaxHp() {
        // A deliberately huge heal (far exceeding the ally's missing HP) so overheal is actually
        // reachable — with the default 12-point heal a below-threshold ally can never overshoot 50 max.
        Harness h = new Harness(999);
        MobInstance ally = h.ally();
        ally.takeDamage(45); // 50 -> 5, badly wounded; the oversized heal would overshoot max HP

        h.registry.tick();

        assertEquals(ally.maxHp(), ally.currentHp(),
            "Healing is clamped to the ally's max HP and never overheals");
    }

    @Test
    void healerAttacksNormallyWhenNoAllyIsWounded() {
        Harness h = new Harness();
        MobInstance ally = h.ally();
        assertEquals(ally.maxHp(), ally.currentHp(), "Precondition: the ally starts at full HP");

        h.registry.tick();

        assertEquals(ally.maxHp(), ally.currentHp(),
            "A full-HP ally is never healed");
        assertFalse(h.contains("chants over"),
            "No heal message when there is nobody to heal");
        assertTrue(h.playerHp() < h.startingPlayerHp(),
            "With no ally to heal, the healer attacks the player like any other mob");
    }

    @Test
    void healerMidTelegraphDoesNotAlsoHeal() {
        Harness h = new Harness();
        MobInstance ally = h.ally();
        ally.takeDamage(30); // wounded, below threshold
        // The healer is already winding up a telegraphed special this decision.
        h.healer().beginTelegraph(HEALER_ATTACK, h.playerName(), 2);

        h.registry.tick();

        assertEquals(20, ally.currentHp(),
            "A healer channeling a telegraph must not also heal on the same decision");
    }

    @Test
    void healerMidFleeDoesNotAlsoHeal() {
        Harness h = new Harness();
        MobInstance ally = h.ally();
        ally.takeDamage(30); // wounded, below threshold
        // Force the healer to flee this decision: engaged, badly wounded, guaranteed flee.
        h.healer().engage(h.playerName());
        h.healer().takeDamage(40); // 45 -> 5
        h.registry.setMobFleeSettings(100, 100);

        h.registry.tick();

        assertEquals(20, ally.currentHp(),
            "A healer breaking off to flee must not also heal on the same decision");
    }

    // ── harness ───────────────────────────────────────────────────────

    private static final class Harness {
        private final MobRegistry registry;
        private final StubPlayerRepository playerRepo;
        private final List<GameMessage> published = new ArrayList<>();
        private final Username playerName;
        private final int startingPlayerHp;

        Harness() {
            this(HEAL_AMOUNT);
        }

        Harness(int healAmount) {
            Player player = player("hero");
            this.playerName = player.getUsername();
            this.startingPlayerHp = player.getVitals().hp();
            MobTemplateRepository templateRepo =
                new StubMobTemplateRepository(List.of(healerTemplate(healAmount), allyTemplate()));
            AttackRepository attackRepo = new StubAttackRepository(ATTACKS);
            RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_A);
            roomService.ensurePlayerLocation(playerName);
            this.playerRepo = new StubPlayerRepository(player);
            PlayerEventBus bus = new PlayerEventBus();
            bus.register(playerName, result -> published.addAll(result.messages()));
            this.registry = new MobRegistry(
                templateRepo, new StubItemRepository(), attackRepo, roomService, playerRepo,
                MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, new FixedRandom(12));
            registry.init();
        }

        MobInstance healer() {
            return mob("Bandit Medic");
        }

        MobInstance ally() {
            return mob("Bandit");
        }

        private MobInstance mob(String name) {
            return registry.getMobsInRoom(ROOM_A).stream()
                .filter(m -> m.template().name().equals(name))
                .findFirst()
                .orElseThrow();
        }

        Username playerName() {
            return playerName;
        }

        int startingPlayerHp() {
            return startingPlayerHp;
        }

        int playerHp() {
            return playerRepo.loadPlayer(playerName).orElseThrow().getVitals().hp();
        }

        boolean contains(String needle) {
            String lowered = needle.toLowerCase(Locale.ROOT);
            return published.stream().anyMatch(m -> m.text().toLowerCase(Locale.ROOT).contains(lowered));
        }
    }

    private static MobTemplate healerTemplate(int healAmount) {
        return new MobTemplate(
            MobId.of("mob.bandit-medic"), "Bandit Medic", 45, HEALER_ATTACK, null, true,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false, null, null, false,
            null, null, false, false, 0, new HealerProfile(healAmount, healAmount, 50));
    }

    private static MobTemplate allyTemplate() {
        // Non-aggressive so it never acts on its own: it exists purely as the healer's heal target.
        return new MobTemplate(
            MobId.of("mob.bandit"), "Bandit", 50, ALLY_ATTACK, null, false,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false);
    }

    private static Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private static final class FixedRandom implements CombatRandom {
        private final int value;

        FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return Math.max(minInclusive, Math.min(maxInclusive, value));
        }

        @Override
        public double nextDouble() {
            return 1.0;
        }
    }

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
                ROOM_A, "Room A", "A ruined courtyard.", Map.of(Direction.NORTH, ROOM_B),
                List.of(), List.of()),
            ROOM_B, new Room(
                ROOM_B, "Room B", "A collapsed tower.", Map.of(Direction.SOUTH, ROOM_A),
                List.of(), List.of())
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
