package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerMount;
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
 * Verifies the Warrior {@code TAUNT} skill at the {@link MobRegistry} layer: an active taunt forces a
 * mob onto the taunter over other engaged candidates, the taunt is dropped (falling back to normal
 * targeting) once the taunter leaves combat, and the rejection paths spend no move points and set no
 * taunt state.
 */
class MobRegistryTauntTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private final Set<Username> hitPlayers = new HashSet<>();

    private Ability tauntSkill() throws Exception {
        return new JsonAbilityRepository(Path.of("data"))
            .findById(AbilityId.of("skill.taunt"))
            .orElseThrow(() -> new AssertionError("skill.taunt must exist in data"));
    }

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate combatMob() {
        return new MobTemplate(
            MobId.of("mob.rat"), "Rat", 100, DEFAULT_ATTACK, null, false, List.of(),
            ROOM_ID, 1, 10, 5, null, null, false);
    }

    /**
     * Builds a registry whose RNG throws whenever the mob AI would fall back to a random target pick
     * (a {@code roll} over a non-degenerate range), so a passing taunt test proves the taunt path
     * bypassed the RNG entirely. Damage rolls over a single-value range are answered normally.
     */
    private MobRegistry buildRegistry(RoomService roomService, PlayerRepository playerRepo, PlayerEventBus bus) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(combatMob()));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository();
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, persistenceQueue,
            bus, new NoRandomTargetPick());
        registry.init();
        return registry;
    }

    private void recordHitsFor(PlayerEventBus bus, Player... players) {
        for (Player p : players) {
            Username name = p.getUsername();
            bus.register(name, result -> {
                if (result.updatedSource() != null) {
                    hitPlayers.add(name);
                }
            });
        }
    }

    @Test
    void taunt_forcesMobOntoTaunterOverOtherCandidate() throws Exception {
        Player tank = player("Thane");
        Player ally = player("Mira");
        StubPlayerRepository playerRepo = new StubPlayerRepository(tank, ally);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(tank.getUsername());
        roomService.ensurePlayerLocation(ally.getUsername());
        PlayerEventBus bus = new PlayerEventBus();
        MobRegistry registry = buildRegistry(roomService, playerRepo, bus);

        // Both players engage the mob so both are valid AI candidates.
        registry.processPlayerAttack(tank, "Rat", ROOM_ID);
        registry.processPlayerAttack(ally, "Rat", ROOM_ID);
        recordHitsFor(bus, tank, ally);

        GameActionResult taunt = registry.processPlayerTaunt(tank, "Rat", tauntSkill(), ROOM_ID);
        assertTrue(taunt.updatedSource() != null, "A successful taunt spends move points");
        assertTrue(taunt.messages().get(0).text().contains("bellow a challenge"));

        // Across the whole taunt duration the mob must strike only the tank, never the ally.
        registry.tick();
        registry.tick();
        registry.tick();

        assertTrue(hitPlayers.contains(tank.getUsername()), "The taunter should be attacked");
        assertFalse(hitPlayers.contains(ally.getUsername()),
            "A taunted mob must ignore other engaged candidates while the taunt holds");
    }

    @Test
    void taunt_dropsWhenTaunterLeavesCombat_fallingBackToRemaining() throws Exception {
        Player tank = player("Thane");
        Player ally = player("Mira");
        StubPlayerRepository playerRepo = new StubPlayerRepository(tank, ally);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(tank.getUsername());
        roomService.ensurePlayerLocation(ally.getUsername());
        PlayerEventBus bus = new PlayerEventBus();
        MobRegistry registry = buildRegistry(roomService, playerRepo, bus);

        registry.processPlayerAttack(tank, "Rat", ROOM_ID);
        registry.processPlayerAttack(ally, "Rat", ROOM_ID);
        registry.processPlayerTaunt(tank, "Rat", tauntSkill(), ROOM_ID);
        MobInstance mob = registry.getMobsInRoom(ROOM_ID).get(0);
        assertEquals(tank.getUsername(), mob.activeTaunter());

        // Tank flees: the taunt is dropped and the mob resumes normal targeting among the remaining
        // engaged players (only the ally is left, so the ally is struck deterministically).
        registry.fleeCombat(tank.getUsername());
        assertNull(mob.activeTaunter(), "The taunt must clear when its taunter leaves combat");
        recordHitsFor(bus, tank, ally);

        registry.tick();

        assertTrue(hitPlayers.contains(ally.getUsername()),
            "With the taunter gone the mob falls back to the remaining engaged ally");
        assertFalse(hitPlayers.contains(tank.getUsername()),
            "A fled player is no longer a candidate");
    }

    @Test
    void taunt_failsWhenMobNotInCombat_noMoveSpentNoTauntSet() throws Exception {
        Player tank = player("Thane");
        StubPlayerRepository playerRepo = new StubPlayerRepository(tank);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(tank.getUsername());
        MobRegistry registry = buildRegistry(roomService, playerRepo, new PlayerEventBus());

        GameActionResult result = registry.processPlayerTaunt(tank, "Rat", tauntSkill(), ROOM_ID);

        assertEquals("The Rat is not fighting anyone.", result.messages().get(0).text());
        assertNull(result.updatedSource(), "A rejected taunt spends no move points");
        assertNull(registry.getMobsInRoom(ROOM_ID).get(0).activeTaunter(),
            "A rejected taunt sets no taunt state");
    }

    @Test
    void taunt_dismountsMountedTaunterOnSuccess_foldedIntoReturnedSource() throws Exception {
        Player tank = player("Thane").withMount(PlayerMount.riding("warhorse", 1));
        Player ally = player("Mira");
        StubPlayerRepository playerRepo = new StubPlayerRepository(tank, ally);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(tank.getUsername());
        roomService.ensurePlayerLocation(ally.getUsername());
        MobRegistry registry = buildRegistry(roomService, playerRepo, new PlayerEventBus());

        // The ally engages the mob so it is in combat and can be taunted.
        registry.processPlayerAttack(ally, "Rat", ROOM_ID);

        GameActionResult taunt = registry.processPlayerTaunt(tank, "Rat", tauntSkill(), ROOM_ID);

        assertTrue(taunt.updatedSource() != null, "A successful taunt returns the updated source");
        assertFalse(taunt.updatedSource().isMounted(),
            "A successful taunt must throw the mounted taunter off their mount");
        assertTrue(taunt.messages().stream().anyMatch(m -> m.text().contains("drop down to fight on foot")),
            "The dismount message must be delivered on a successful mounted taunt");
    }

    @Test
    void taunt_failedTaunt_leavesMountIntactNoDismountMessage() throws Exception {
        Player tank = player("Thane").withMount(PlayerMount.riding("warhorse", 1));
        StubPlayerRepository playerRepo = new StubPlayerRepository(tank);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(tank.getUsername());
        MobRegistry registry = buildRegistry(roomService, playerRepo, new PlayerEventBus());

        // No such mob: the taunt is rejected before any state change.
        GameActionResult result = registry.processPlayerTaunt(tank, "Dragon", tauntSkill(), ROOM_ID);

        assertEquals("No such target here.", result.messages().get(0).text());
        assertNull(result.updatedSource(), "A rejected taunt returns no updated source");
        assertTrue(tank.isMounted(), "A failed taunt must not dismount the rider");
        assertFalse(result.messages().stream().anyMatch(m -> m.text().contains("drop down to fight on foot")),
            "A failed taunt must not emit a spurious dismount message");
    }

    @Test
    void taunt_failsWhenNoSuchMob() throws Exception {
        Player tank = player("Thane");
        StubPlayerRepository playerRepo = new StubPlayerRepository(tank);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(tank.getUsername());
        MobRegistry registry = buildRegistry(roomService, playerRepo, new PlayerEventBus());

        GameActionResult result = registry.processPlayerTaunt(tank, "Dragon", tauntSkill(), ROOM_ID);

        assertEquals("No such target here.", result.messages().get(0).text());
        assertNull(result.updatedSource());
    }

    // ── stubs ─────────────────────────────────────────────────────────

    /** RNG that answers single-value rolls but rejects any genuine random target pick. */
    private static final class NoRandomTargetPick implements CombatRandom {
        @Override
        public int roll(int minInclusive, int maxInclusive) {
            if (minInclusive != maxInclusive) {
                throw new AssertionError(
                    "Mob AI consulted the RNG for target selection despite an active taunt");
            }
            return minInclusive;
        }
    }

    private record StubMobTemplateRepository(List<MobTemplate> templates) implements MobTemplateRepository {
        @Override public List<MobTemplate> findAll() { return templates; }
    }

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks) implements AttackRepository {
        @Override public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubItemRepository implements ItemRepository {
        @Override public void save(Item item) {}
        @Override public Optional<Item> findById(ItemId id) { return Optional.empty(); }
    }

    private static final class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();
        StubPlayerRepository(Player... players) {
            for (Player player : players) {
                store.put(player.getUsername(), player);
            }
        }
        @Override public void savePlayer(Player player) { store.put(player.getUsername(), player); }
        @Override public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
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
