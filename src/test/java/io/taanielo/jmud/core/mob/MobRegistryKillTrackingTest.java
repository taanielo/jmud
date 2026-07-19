package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
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
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.quest.ActiveQuest;
import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Verifies that {@code totalKills} is incremented on the killing player each
 * time a mob is killed, via both the instant-kill ({@code processPlayerAttack})
 * and the tick-based ({@code runPlayerCombat}) paths.
 */
class MobRegistryKillTrackingTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK =
        AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    // ── helpers ───────────────────────────────────────────────────────

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate templateWithHp(int maxHp) {
        return new MobTemplate(
            MobId.of("mob.rat"),
            "Rat",
            maxHp,
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
    }

    private MobRegistry buildRegistry(
        MobTemplate template,
        Player attacker,
        StubPlayerRepository playerRepo,
        List<GameActionResult> captured,
        PersistenceQueue persistenceQueue
    ) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository();

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(attacker.getUsername());

        PlayerEventBus bus = new PlayerEventBus();
        bus.register(attacker.getUsername(), captured::add);

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, persistenceQueue, bus,
            MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    // ── tests ─────────────────────────────────────────────────────────

    /**
     * Killing a mob via {@code processPlayerAttack} must increment
     * {@code totalKills} from 0 to 1 on the persisted player.
     */
    @Test
    void processPlayerAttack_incrementsTotalKillsOnKill() {
        Player attacker = player("hero");
        assertEquals(0L, attacker.getTotalKills(), "Player should start with 0 kills");

        MobTemplate template = templateWithHp(1); // 1 HP → one-shot
        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(template, attacker, playerRepo, new ArrayList<>(), persistenceQueue);

        registry.processPlayerAttack(attacker, "Rat", ROOM_ID);

        // Saves are write-behind (issue #179): wait for the queue to drain before
        // reading back the persisted state.
        assertTrue(persistenceQueue.flush(Duration.ofSeconds(2)));
        persistenceQueue.close();
        Player saved = playerRepo.load(attacker.getUsername());
        assertEquals(1L, saved.getTotalKills(),
            "totalKills should be 1 after one kill via processPlayerAttack");
    }

    /**
     * Killing a second mob must increment {@code totalKills} to 2.
     */
    @Test
    void processPlayerAttack_accumulatesTotalKillsAcrossMultipleKills() {
        Player attacker = player("hero");

        // Use two separate registries / mob instances to simulate two distinct kills.
        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        List<GameActionResult> captured = new ArrayList<>();

        MobTemplate template = templateWithHp(1);
        MobRegistry registry = buildRegistry(template, attacker, playerRepo, captured, persistenceQueue);

        registry.processPlayerAttack(attacker, "Rat", ROOM_ID);

        // Reload the saved state and simulate a second kill.
        assertTrue(persistenceQueue.flush(Duration.ofSeconds(2)));
        persistenceQueue.close();
        Player afterFirst = playerRepo.load(attacker.getUsername());
        assertEquals(1L, afterFirst.getTotalKills());

        // The second kill reuses the same registry — mob respawns after scheduleRespawn,
        // so we rebuild to get a fresh instance.
        StubPlayerRepository playerRepo2 = new StubPlayerRepository(afterFirst);
        PersistenceQueue persistenceQueue2 = MobRegistryTestSupport.persistenceQueueFor(playerRepo2);
        MobRegistry registry2 = buildRegistry(template, afterFirst, playerRepo2, new ArrayList<>(), persistenceQueue2);
        registry2.processPlayerAttack(afterFirst, "Rat", ROOM_ID);

        assertTrue(persistenceQueue2.flush(Duration.ofSeconds(2)));
        persistenceQueue2.close();
        Player afterSecond = playerRepo2.load(afterFirst.getUsername());
        assertEquals(2L, afterSecond.getTotalKills(),
            "totalKills should be 2 after two kills");
    }

    /**
     * Killing a mob via the tick ({@code runPlayerCombat}) must also
     * increment {@code totalKills}.
     */
    @Test
    void runPlayerCombat_incrementsTotalKillsOnKill() {
        Player attacker = player("hero");

        // 2 HP: first attack leaves 1 HP, next tick delivers the killing blow.
        MobTemplate template = templateWithHp(2);
        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        List<GameActionResult> captured = new ArrayList<>();
        MobRegistry registry = buildRegistry(template, attacker, playerRepo, captured, persistenceQueue);

        // First hit — mob survives, combat registered.
        registry.processPlayerAttack(attacker, "Rat", ROOM_ID);
        captured.clear();

        // Tick — killing blow via runPlayerCombat.
        registry.tick();

        assertTrue(persistenceQueue.flush(Duration.ofSeconds(2)));
        persistenceQueue.close();
        Player saved = playerRepo.load(attacker.getUsername());
        assertEquals(1L, saved.getTotalKills(),
            "totalKills should be 1 after a tick kill via runPlayerCombat");
    }

    // ── quest-kill decrement regression (issue #798) ──────────────────

    private static final QuestId RAT_CATCHER_ID = QuestId.of("rat-catcher");

    private QuestTemplate ratCatcher(int requiredKills) {
        // The mob template used by these tests reports id "mob.rat", so the quest must target it.
        return new QuestTemplate(
            RAT_CATCHER_ID, "Rat Catcher", "Kill rats.", "mob.rat", requiredKills, 30, 75);
    }

    /**
     * A quest-tracked kill via {@code processPlayerAttack} must expose the rewarded player (with the
     * quest slot decremented) as the result's {@code updatedSource}, so the caller can sync the live
     * session's cached player. Before the fix (issue #798) the kill path returned a {@code null}
     * source and reloaded the player from disk, leaving the session cache stale so a following
     * healing/HP tick re-saved the pre-kill quest state over the decrement.
     */
    @Test
    void processPlayerAttack_exposesDecrementedQuestAsUpdatedSource() {
        Player attacker = player("hero").withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 5));
        MobTemplate template = templateWithHp(1); // one-shot
        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(template, attacker, playerRepo, new ArrayList<>(), persistenceQueue);
        registry.setQuestKillService(new QuestKillService(new StubQuestRepository(List.of(ratCatcher(5)))));

        GameActionResult result = registry.processPlayerAttack(attacker, "Rat", ROOM_ID);

        assertNotNull(result.updatedSource(),
            "A quest kill must return the rewarded player as updatedSource, not null (issue #798)");
        ActiveQuest updatedQuest = result.updatedSource().getActiveQuest();
        assertNotNull(updatedQuest);
        assertEquals(4, updatedQuest.killsRemaining(),
            "updatedSource must carry the decremented quest-kill count");

        assertTrue(persistenceQueue.flush(Duration.ofSeconds(2)));
        persistenceQueue.close();
    }

    /**
     * Reproduces the core race of issue #798: a stale pre-kill player snapshot re-saved to disk
     * (exactly what the per-tick healing/HP ticker did with its cached session player) must not
     * cause a subsequent kill to lose quest progress. The fix makes the kill path use the
     * caller-supplied (session-synced) player as its base rather than a fresh disk reload, so the
     * second kill decrements from {@code killsRemaining=1} to {@code 0} (quest complete) instead of
     * re-reading the clobbered {@code killsRemaining=2} and stalling at {@code 1}.
     */
    @Test
    void questKillDecrement_survivesStaleDiskReSaveBetweenKills() {
        QuestTemplate template = ratCatcher(2); // two kills to complete
        Player attacker = player("hero").withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 2));
        MobTemplate mobTemplate = templateWithHp(1);

        // First kill: decrement 2 -> 1, exposed as updatedSource for the session to adopt.
        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = buildRegistry(mobTemplate, attacker, playerRepo, new ArrayList<>(), persistenceQueue);
        registry.setQuestKillService(new QuestKillService(new StubQuestRepository(List.of(template))));

        GameActionResult first = registry.processPlayerAttack(attacker, "Rat", ROOM_ID);
        assertNotNull(first.updatedSource());
        Player synced = first.updatedSource();
        assertEquals(1, synced.getActiveQuest().killsRemaining());
        assertTrue(persistenceQueue.flush(Duration.ofSeconds(2)));
        persistenceQueue.close();

        // Second kill against a fresh mob instance. The on-disk copy is deliberately the STALE
        // pre-kill snapshot (killsRemaining=2), mimicking a healing tick that re-saved the un-synced
        // session player. The kill must decrement from the synced session player (killsRemaining=1),
        // not from that stale disk state.
        StubPlayerRepository stalePlayerRepo = new StubPlayerRepository(attacker); // disk: killsRemaining=2
        PersistenceQueue persistenceQueue2 = MobRegistryTestSupport.persistenceQueueFor(stalePlayerRepo);
        MobRegistry registry2 =
            buildRegistry(mobTemplate, synced, stalePlayerRepo, new ArrayList<>(), persistenceQueue2);
        registry2.setQuestKillService(new QuestKillService(new StubQuestRepository(List.of(template))));

        GameActionResult second = registry2.processPlayerAttack(synced, "Rat", ROOM_ID);
        assertNotNull(second.updatedSource());
        ActiveQuest afterSecond = second.updatedSource().getActiveQuest();
        assertEquals(0, afterSecond.killsRemaining(),
            "Second kill must decrement from the synced player, not the stale disk reload (issue #798)");
        assertTrue(afterSecond.isComplete(), "Quest should be fulfilled after two kills");

        assertTrue(persistenceQueue2.flush(Duration.ofSeconds(2)));
        persistenceQueue2.close();
    }

    /**
     * The tick-driven auto-attack path ({@code runPlayerCombat}) — the one the golden-path smoke test
     * exercises — must publish the rewarded player as the event's {@code updatedSource} so the
     * session's cached player (and thus the next healing-tick save) reflects the quest decrement.
     */
    @Test
    void runPlayerCombat_publishesDecrementedQuestAsUpdatedSource() {
        Player attacker = player("hero").withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 5));
        MobTemplate template = templateWithHp(2); // survives first hit, dies on the tick
        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        List<GameActionResult> captured = new ArrayList<>();
        MobRegistry registry = buildRegistry(template, attacker, playerRepo, captured, persistenceQueue);
        registry.setQuestKillService(new QuestKillService(new StubQuestRepository(List.of(ratCatcher(5)))));

        registry.processPlayerAttack(attacker, "Rat", ROOM_ID); // engage; mob survives
        captured.clear();

        registry.tick(); // killing blow via runPlayerCombat

        GameActionResult killEvent = captured.getLast();
        assertNotNull(killEvent.updatedSource(),
            "The tick kill must publish the rewarded player as updatedSource (issue #798)");
        assertEquals(4, killEvent.updatedSource().getActiveQuest().killsRemaining(),
            "Published updatedSource must carry the decremented quest-kill count");

        assertTrue(persistenceQueue.flush(Duration.ofSeconds(2)));
        persistenceQueue.close();
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubQuestRepository(List<QuestTemplate> templates) implements QuestRepository {
        @Override
        public List<QuestTemplate> findAll() { return templates; }
        @Override
        public Optional<QuestTemplate> findById(QuestId id) {
            return templates.stream().filter(t -> t.id().equals(id)).findFirst();
        }
    }

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
        @Override public void save(Item item) {}
        @Override public Optional<Item> findById(ItemId id) { return Optional.empty(); }
    }

    static class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();
        StubPlayerRepository(Player initial) { store.put(initial.getUsername(), initial); }
        @Override public void savePlayer(Player player) { store.put(player.getUsername(), player); }
        @Override public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
        Player load(Username username) { return store.get(username); }
    }

    private static class StubRoomRepository implements RoomRepository {
        private final Room room;
        StubRoomRepository(RoomId roomId) {
            this.room = new Room(roomId, "Test Room", "A void.", Map.of(), List.of(), List.of());
        }
        @Override public void save(Room room) {}
        @Override public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
