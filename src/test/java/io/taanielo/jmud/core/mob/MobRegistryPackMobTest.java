package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
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
 * Unit tests for {@code pack}-tagged mob assist behaviour: a non-aggressive pack mob never starts a
 * fresh fight on its own, but joins one already begun against a room-mate, and still respects stealth
 * for its own first engagement. Mirrors {@link MobRegistryAggressiveMobTest}'s structure.
 */
class MobRegistryPackMobTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "bite", 1, 1, 0, 0, 0, List.of());
    private static final String PACK_JOIN_LINE =
        "Another Wolf snarls and lunges to its packmate's defense!";

    private final List<String> heroMessages = new ArrayList<>();
    private StubPlayerRepository playerRepo;

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    /** Two pack wolves (maxCount 2), non-aggressive, in {@link #ROOM_ID}. */
    private MobTemplate packWolves() {
        return new MobTemplate(
            MobId.of("mob.wolf"), "Wolf", 100, DEFAULT_ATTACK, null, false, List.of(),
            ROOM_ID, 2, 10, 5, null, List.of("pack"), false);
    }

    private MobRegistry buildRegistry(Player hero) {
        StubPlayerRepository playerRepo = new StubPlayerRepository(hero);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(hero.getUsername());

        PlayerEventBus bus = new PlayerEventBus();
        bus.register(hero.getUsername(), result -> {
            for (var message : result.messages()) {
                heroMessages.add(message.text());
            }
        });

        MobRegistry registry = new MobRegistry(
            new StubMobTemplateRepository(List.of(packWolves())),
            new StubItemRepository(),
            new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK)),
            roomService,
            playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo),
            bus,
            MobRegistryTestSupport.random());
        registry.init();
        // Full-HP wolves never flee; pin the tunables so the AI decision is deterministic.
        registry.setMobFleeSettings(0, 0);
        this.playerRepo = playerRepo;
        return registry;
    }

    private long engagedMobCount(MobRegistry registry, Username username) {
        return registry.getMobsInRoom(ROOM_ID).stream()
            .filter(m -> m.engagedPlayers().contains(username))
            .count();
    }

    private long packJoinLineCount() {
        return heroMessages.stream().filter(PACK_JOIN_LINE::equals).count();
    }

    @Test
    void packMob_doesNotEngageOnItsOwn_afterTick() {
        Player hero = player("hero");
        MobRegistry registry = buildRegistry(hero);

        registry.tick();

        assertFalse(registry.isInCombat(hero.getUsername()),
            "A pack mob must not start a fresh fight on its own");
        assertEquals(0, engagedMobCount(registry, hero.getUsername()),
            "No pack mob should have engaged the idle player");
        assertEquals(0, packJoinLineCount(), "No packmate-defence line should be sent");
    }

    @Test
    void packMob_joinsFightStartedByPlayer_withinATick() {
        Player hero = player("hero");
        MobRegistry registry = buildRegistry(hero);

        // The player starts the fight by attacking one wolf; only that wolf is engaged so far.
        registry.processPlayerAttack(hero, "Wolf", ROOM_ID);
        assertEquals(1, engagedMobCount(registry, hero.getUsername()),
            "Precondition: only the attacked wolf is engaged before the tick");

        registry.tick();

        assertEquals(2, engagedMobCount(registry, hero.getUsername()),
            "The room-mate pack wolf should join the fight against the player within a tick");
        assertEquals(1, packJoinLineCount(),
            "The joining packmate should announce itself with the packmate-defence line");
    }

    @Test
    void packMob_doesNotDoubleJoinAPlayerItIsAlreadyEngagedWith() {
        Player hero = player("hero");
        MobRegistry registry = buildRegistry(hero);

        registry.processPlayerAttack(hero, "Wolf", ROOM_ID);
        registry.tick();
        assertEquals(2, engagedMobCount(registry, hero.getUsername()),
            "Precondition: both wolves are engaged after the first tick");

        registry.tick();

        assertEquals(2, engagedMobCount(registry, hero.getUsername()),
            "An already-engaged pack mob must not re-join, and no third wolf exists");
        assertEquals(1, packJoinLineCount(),
            "The packmate-defence line must be sent exactly once, not on every subsequent tick");
    }

    @Test
    void packMob_stillObeysStealthForItsOwnFirstEngagement() {
        Player hero = player("hero");
        MobRegistry registry = buildRegistry(hero);

        // The player attacks one wolf (engaging it and breaking stealth), then slips back into
        // stealth. The room-mate wolf has an engaged room-mate but a hidden target: it must not join.
        registry.processPlayerAttack(hero, "Wolf", ROOM_ID);
        playerRepo.savePlayer(hero.withStealth(true));

        registry.tick();

        assertEquals(1, engagedMobCount(registry, hero.getUsername()),
            "A pack mob must not join against a stealthed player for its own first engagement");
        assertEquals(0, packJoinLineCount(),
            "No packmate should announce a join against the hidden player");
    }

    // ── stubs ─────────────────────────────────────────────────────────

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
        @Override public void savePlayer(Player player) {
            // Stealth is an in-memory-only live flag ({@code Player.withStealth} is never persisted):
            // the write-behind persistence queue strips it via {@code snapshotForPersistence} before a
            // save reaches this repository. This stub stands in for the live player view that the mob
            // AI stealth gate is designed to read, so — like a real live-player store — it must not let
            // a stripped combat snapshot clobber a player's active stealth. Without this, a roommate
            // mob's mid-tick combat save would wipe the hero's stealth before the pack mob evaluates,
            // making the stealth gate untestable (and its outcome dependent on write-behind timing).
            Player existing = store.get(player.getUsername());
            Player toStore = existing != null && existing.isStealthActive() && !player.isStealthActive()
                ? player.withStealth(true)
                : player;
            store.put(player.getUsername(), toStore);
        }
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
