package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
 * Verifies the boss enrage mechanic (issue #745): a fight against an enrage-capable mob that runs past
 * its authored threshold enrages exactly once, announces it to the room, and boosts the mob's landed
 * damage by the authored multiplier for the rest of the encounter. Also covers the reviewed
 * interactions: a fleeing (#567), healing (#733), or freshly pack-joining (#617) decision never also
 * enrages on the same tick, and an ordinary (non-enrage) mob is numerically unchanged.
 */
class MobRegistryEnrageTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");

    private static final AttackId BASIC_ATTACK = AttackId.of("attack.basic");
    private static final AttackDefinition BASIC_MELEE =
        new AttackDefinition(BASIC_ATTACK, "strike", 1, 1, 0, 0, 0, List.of());
    private static final Map<AttackId, AttackDefinition> ATTACKS = Map.of(BASIC_ATTACK, BASIC_MELEE);

    private static final int HEAL_AMOUNT = 12;
    private static final String ENRAGE_LINE =
        "'s eyes blaze with fury — it grows enraged!";

    @Test
    void fightPastThresholdEnragesOnceAndBoostsDamage() {
        // threshold 3, x3 damage. Gated on !firstEngagement, so the opening swing starts the clock:
        // ticks 2,3,4 advance it, and tick 4 crosses the threshold and enrages.
        Harness h = new Harness(List.of(enrageBoss(3, 3.0)));

        for (int i = 0; i < 3; i++) {
            h.registry.tick();
        }
        assertEquals(0, h.enrageMessageCount(), "the mob must not enrage before the threshold");
        assertEquals(17, h.lastPlayerHp(), "three unboosted 1-damage hits leave the player at 17");

        h.registry.tick(); // 4th sustained decision crosses the threshold
        assertEquals(1, h.enrageMessageCount(), "the mob enrages exactly once when the threshold is crossed");
        assertTrue(h.contains("The Boss" + ENRAGE_LINE), "the enrage announcement names the mob");
        assertEquals(14, h.lastPlayerHp(), "the enraging hit already lands boosted 3 damage (17 -> 14)");

        h.registry.tick();
        assertEquals(1, h.enrageMessageCount(), "enrage announces only once per encounter");
        assertEquals(11, h.lastPlayerHp(), "the boost persists for the rest of the fight (14 -> 11)");
    }

    @Test
    void ordinaryMobNeverEnragesAndDealsUnboostedDamage() {
        Harness h = new Harness(List.of(nonEnrageBoss()));

        for (int i = 0; i < 6; i++) {
            h.registry.tick();
        }
        assertEquals(0, h.enrageMessageCount(), "a mob with no enrage threshold never enrages");
        assertEquals(20 - 6, h.lastPlayerHp(), "every hit stays at the unboosted 1 damage");
    }

    @Test
    void fleeingDecisionDoesNotAlsoEnrage() {
        // threshold 1: any advanced decision would enrage, proving the flee gate short-circuits first.
        Harness h = new Harness(List.of(enrageBoss(1, 3.0)));
        MobInstance boss = h.mob("the Boss");
        boss.engage(h.playerName);
        boss.takeDamage(boss.currentHp() - 1); // 1 HP: guaranteed below the flee threshold
        h.registry.setMobFleeSettings(100, 100);

        h.registry.tick();

        assertEquals(0, h.enrageMessageCount(),
            "a mob breaking off to flee must not also enrage on the same decision");
    }

    @Test
    void healingDecisionDoesNotAlsoEnrage() {
        // threshold 1: any advanced decision would enrage, proving the heal gate short-circuits first.
        Harness h = new Harness(List.of(enrageHealer(1), ally()));
        MobInstance healer = h.mob("the Medic");
        MobInstance ally = h.mob("Wounded Ally");
        healer.engage(h.playerName);
        ally.takeDamage(30); // 50 -> 20, below the 50% heal threshold

        h.registry.tick();

        assertEquals(20 + HEAL_AMOUNT, ally.currentHp(), "the healer spends the decision mending its ally");
        assertEquals(0, h.enrageMessageCount(),
            "a mob spending its decision healing must not also enrage on the same tick");
    }

    @Test
    void packJoinDoesNotEnrageOnTheJoinTick() {
        // The pack mob (threshold 1) reinforces a fight the leader already holds. Its join is a first
        // engagement, so it starts its clock rather than crossing it — no enrage on the join tick.
        Harness h = new Harness(List.of(leader(), packMob(1)));
        MobInstance leader = h.mob("the Leader");
        leader.engage(h.playerName); // an existing fight for the pack mob to reinforce

        h.registry.tick(); // pack mob joins this tick (first engagement)
        assertEquals(0, h.enrageMessageCount(),
            "a pack mob must not enrage on the very tick it joins the fight");

        h.registry.tick(); // now already engaged: its clock advances and crosses the threshold
        assertEquals(1, h.enrageMessageCount(),
            "once engaged, the pack mob's clock advances and it enrages as normal");
        assertTrue(h.contains("The Pack Hound" + ENRAGE_LINE));
    }

    // ── templates ─────────────────────────────────────────────────────

    private static MobTemplate enrageBoss(int threshold, double multiplier) {
        return new MobTemplate(
            MobId.of("mob.boss"), "the Boss", 100, BASIC_ATTACK, null, true,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false,
            null, null, false, null, null, false, false, 0, Map.of(), Map.of(), null,
            threshold, multiplier);
    }

    private static MobTemplate nonEnrageBoss() {
        return new MobTemplate(
            MobId.of("mob.boss"), "the Boss", 100, BASIC_ATTACK, null, true,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false);
    }

    private static MobTemplate enrageHealer(int threshold) {
        return new MobTemplate(
            MobId.of("mob.medic"), "the Medic", 100, BASIC_ATTACK, null, true,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false,
            null, null, false, null, null, false, false, 0, Map.of(), Map.of(),
            new HealerProfile(HEAL_AMOUNT, HEAL_AMOUNT, 50), threshold, 2.0);
    }

    private static MobTemplate ally() {
        // Non-aggressive so it never acts on its own: it exists purely as the healer's heal target.
        return new MobTemplate(
            MobId.of("mob.ally"), "Wounded Ally", 50, BASIC_ATTACK, null, false,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false);
    }

    private static MobTemplate leader() {
        return new MobTemplate(
            MobId.of("mob.leader"), "the Leader", 100, BASIC_ATTACK, null, true,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false);
    }

    private static MobTemplate packMob(int threshold) {
        return new MobTemplate(
            MobId.of("mob.pack"), "the Pack Hound", 100, BASIC_ATTACK, null, false,
            List.of(), ROOM_A, 1, 10, 5, null, List.of("pack"), false,
            null, null, false, null, null, false, false, 0, Map.of(), Map.of(), null,
            threshold, 2.0);
    }

    private static Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    // ── harness ───────────────────────────────────────────────────────

    private static final class Harness {
        private final MobRegistry registry;
        private final Username playerName;
        private final List<GameMessage> published = new ArrayList<>();
        private final AtomicReference<Player> lastSource = new AtomicReference<>();

        Harness(List<MobTemplate> templates) {
            Player player = player("hero");
            this.playerName = player.getUsername();
            MobTemplateRepository templateRepo = new StubMobTemplateRepository(templates);
            AttackRepository attackRepo = new StubAttackRepository(ATTACKS);
            RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_A);
            roomService.ensurePlayerLocation(playerName);
            StubPlayerRepository playerRepo = new StubPlayerRepository(player);
            PlayerEventBus bus = new PlayerEventBus();
            bus.register(playerName, this::capture);
            this.registry = new MobRegistry(
                templateRepo, new StubItemRepository(), attackRepo, roomService, playerRepo,
                MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus,
                MobRegistryTestSupport.random());
            registry.init();
        }

        private void capture(GameActionResult result) {
            published.addAll(result.messages());
            if (result.updatedSource() != null) {
                lastSource.set(result.updatedSource());
            }
        }

        MobInstance mob(String name) {
            return registry.getMobsInRoom(ROOM_A).stream()
                .filter(m -> m.template().name().equals(name))
                .findFirst()
                .orElseThrow();
        }

        int enrageMessageCount() {
            return (int) published.stream().filter(m -> m.text().contains(ENRAGE_LINE)).count();
        }

        boolean contains(String needle) {
            return published.stream().anyMatch(m -> m.text().contains(needle));
        }

        int lastPlayerHp() {
            Player source = lastSource.get();
            if (source == null) {
                throw new IllegalStateException("No damaging hit was published yet");
            }
            return source.getVitals().hp();
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
