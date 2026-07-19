package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.taanielo.jmud.core.party.PartyService;
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
 * Unit tests for AUTOASSIST auto-join fan-out (issue #709): when a party leader lands their opening
 * attack on a fresh mob, every other party member in the same room with AUTOASSIST enabled and not
 * already fighting/resting/dead is automatically joined to the fight, and only once per new
 * engagement. Mirrors {@link MobRegistryPackMobTest}'s stub structure.
 */
class MobRegistryAutoAssistTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "bite", 1, 1, 0, 0, 0, List.of());

    private final List<String> memberMessages = new ArrayList<>();
    private StubPlayerRepository playerRepo;
    private PartyService partyService;

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    /** A single non-aggressive, tough (100 HP) rat so an opening swing never kills it. */
    private MobTemplate rats(int count) {
        return new MobTemplate(
            MobId.of("mob.rat"), "Rat", 100, DEFAULT_ATTACK, null, false, List.of(),
            ROOM_ID, count, 10, 5, null, List.of(), false);
    }

    /**
     * Builds a registry with {@code leader} and {@code member} both present in {@link #ROOM_ID}, with
     * {@code count} rats spawned. When {@code partied} is true they are placed in the same party.
     */
    private MobRegistry buildRegistry(Player leader, Player member, boolean partied, int count) {
        playerRepo = new StubPlayerRepository(leader, member);
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(leader.getUsername());
        roomService.ensurePlayerLocation(member.getUsername());

        PlayerEventBus bus = new PlayerEventBus();
        bus.register(member.getUsername(), result -> {
            for (var message : result.messages()) {
                memberMessages.add(message.text());
            }
        });

        partyService = new PartyService();
        if (partied) {
            partyService.form(leader.getUsername());
            partyService.invite(leader.getUsername(), member.getUsername(), true);
            partyService.accept(member.getUsername());
        }

        MobRegistry registry = new MobRegistry(
            new StubMobTemplateRepository(List.of(rats(count))),
            new StubItemRepository(),
            new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK)),
            roomService,
            playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo),
            bus,
            MobRegistryTestSupport.random());
        registry.setPartyService(partyService);
        registry.init();
        registry.setMobFleeSettings(0, 0);
        return registry;
    }

    private long autoJoinLineCount() {
        return memberMessages.stream()
            .filter(m -> m.contains("You automatically join") && m.contains("Rat"))
            .count();
    }

    @Test
    void enabledPartyMemberAutoJoinsLeadersFreshFight() {
        Player leader = player("leader");
        Player member = player("member").withAutoAssistEnabled(true);
        MobRegistry registry = buildRegistry(leader, member, true, 1);

        registry.processPlayerAttack(leader, "Rat", ROOM_ID);

        assertTrue(registry.isInCombat(member.getUsername()),
            "An AUTOASSIST-enabled party member should be auto-joined to the leader's fresh fight");
        assertEquals(1, autoJoinLineCount(),
            "The auto-joined member should receive exactly one auto-join notice");
    }

    @Test
    void disabledMemberIsNotAutoJoined() {
        Player leader = player("leader");
        Player member = player("member"); // AUTOASSIST defaults OFF
        MobRegistry registry = buildRegistry(leader, member, true, 1);

        registry.processPlayerAttack(leader, "Rat", ROOM_ID);

        assertFalse(registry.isInCombat(member.getUsername()),
            "A member with AUTOASSIST off must never be auto-joined");
        assertEquals(0, autoJoinLineCount());
    }

    @Test
    void nonPartiedBystanderIsNotAutoJoined() {
        Player leader = player("leader");
        Player member = player("member").withAutoAssistEnabled(true);
        MobRegistry registry = buildRegistry(leader, member, false, 1);

        registry.processPlayerAttack(leader, "Rat", ROOM_ID);

        assertFalse(registry.isInCombat(member.getUsername()),
            "A non-partied bystander must never be auto-joined, even with AUTOASSIST on");
        assertEquals(0, autoJoinLineCount());
    }

    @Test
    void memberAlreadyInCombatIsNotPulledOntoNewTarget() {
        Player leader = player("leader");
        Player member = player("member").withAutoAssistEnabled(true);
        MobRegistry registry = buildRegistry(leader, member, true, 2);

        // Member is already fighting one rat; the leader then opens on a fresh second rat.
        registry.processPlayerAttack(member, "Rat", ROOM_ID);
        memberMessages.clear();

        registry.processPlayerAttack(leader, "Rat", ROOM_ID);

        // The member stays engaged with exactly one mob (their own), not pulled onto the leader's.
        assertEquals(1, registry.getMobsInRoom(ROOM_ID).stream()
                .filter(m -> m.engagedPlayers().contains(member.getUsername()))
                .count(),
            "A member already in combat must not be pulled onto a new target");
        assertEquals(0, autoJoinLineCount());
    }

    @Test
    void restingMemberIsNotPulledIntoCombat() {
        Player leader = player("leader");
        Player member = player("member").withAutoAssistEnabled(true).withResting(true);
        MobRegistry registry = buildRegistry(leader, member, true, 1);

        registry.processPlayerAttack(leader, "Rat", ROOM_ID);

        assertFalse(registry.isInCombat(member.getUsername()),
            "A resting member must never be auto-joined");
        assertEquals(0, autoJoinLineCount());
    }

    @Test
    void deadMemberIsNotPulledIntoCombat() {
        Player leader = player("leader");
        Player member = player("member").withAutoAssistEnabled(true).withDead(true);
        MobRegistry registry = buildRegistry(leader, member, true, 1);

        registry.processPlayerAttack(leader, "Rat", ROOM_ID);

        assertFalse(registry.isInCombat(member.getUsername()),
            "A dead member must never be auto-joined");
        assertEquals(0, autoJoinLineCount());
    }

    @Test
    void autoAssistFiresOnceNotOnSubsequentSwings() {
        Player leader = player("leader");
        Player member = player("member").withAutoAssistEnabled(true);
        MobRegistry registry = buildRegistry(leader, member, true, 1);

        registry.processPlayerAttack(leader, "Rat", ROOM_ID);
        // The mob is now already engaged; a second swing must not re-fan-out auto-assist.
        registry.processPlayerAttack(leader, "Rat", ROOM_ID);

        assertEquals(1, autoJoinLineCount(),
            "Auto-assist must fire once per fresh engagement, not on every subsequent swing");
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
            store.put(player.getUsername(), player);
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
