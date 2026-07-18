package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.mentor.MentorService;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Verifies the mentor bond's kill-time effects (issue #751): a mentee whose mentor is also an
 * eligible recipient of the same party kill earns a flat +20% bonus on their own XP share (additive,
 * never taken from anyone else), and a mentee who reaches the graduation threshold auto-graduates —
 * ending the bond on both sides, incrementing the mentor's counter, and granting the mentor title.
 */
class MobRegistryMentorBonusTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private final Map<Username, List<GameActionResult>> captured = new ConcurrentHashMap<>();
    private StubPlayerRepository playerRepo;
    private PersistenceQueue queue;

    private Player leveled(String name, int level) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return new Player(user, level, 0L, PlayerVitals.defaults(), List.of(),
            "%hp> ", false, List.of(), null, null);
    }

    /** A one-HP goblin that grants {@code xp} experience and drops no gold. */
    private MobTemplate goblin(int xp) {
        return new MobTemplate(
            MobId.of("mob.goblin"), "Goblin", 1, DEFAULT_ATTACK, null, false,
            List.of(), ROOM_ID, 1, 10, xp, null, null, false);
    }

    private MobRegistry buildRegistry(MobTemplate template, Player... players) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        for (Player p : players) {
            roomService.ensurePlayerLocation(p.getUsername());
        }

        playerRepo = new StubPlayerRepository(List.of(players));

        PlayerEventBus bus = new PlayerEventBus();
        for (Player p : players) {
            captured.put(p.getUsername(), new ArrayList<>());
            bus.register(p.getUsername(), captured.get(p.getUsername())::add);
        }

        queue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, queue, bus,
            MobRegistryTestSupport.random());
        registry.setLevelUpService(new LevelUpService());

        PartyService partyService = new PartyService();
        Username leader = players[0].getUsername();
        partyService.form(leader);
        for (int i = 1; i < players.length; i++) {
            partyService.invite(leader, players[i].getUsername(), true);
            partyService.accept(players[i].getUsername());
        }
        registry.setPartyService(partyService);
        registry.setMentorService(new MentorService());
        registry.init();
        return registry;
    }

    private List<String> capturedTexts(Username username) {
        return captured.get(username).stream()
            .flatMap(r -> r.messages().stream())
            .map(GameMessage::text)
            .toList();
    }

    private Player saved(Username username) {
        queue.flush(Duration.ofSeconds(5));
        return playerRepo.loadPlayer(username).orElseThrow();
    }

    @Test
    void menteeEarnsFlatBonusOnTheirOwnShareWhileMentorPresent() {
        // Mentor is 15 levels above; the mentee (level 5) is well below the graduation threshold (10).
        Player mentor = leveled("Mentor", 20).withMentee("Mentee", 1000L);
        Player mentee = leveled("Mentee", 5).withMentor("Mentor", 1000L);
        // xpReward 100, party of two → 50 each; the mentee's own share is boosted by +20% = 10.
        MobRegistry registry = buildRegistry(goblin(100), mentee, mentor);

        GameActionResult result = registry.processPlayerAttack(mentee, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("Your mentor's guidance grants you 10 bonus experience!")),
            "Mentee (the killer) should see their mentor bonus: "
                + result.messages().stream().map(GameMessage::text).toList());
        assertFalse(capturedTexts(mentor.getUsername()).stream()
                .anyMatch(t -> t.contains("mentor's guidance")),
            "The mentor gains no bonus XP themselves");

        // Mentee received 50 base + 10 bonus = 60; mentor received the plain 50 share. Neither levels.
        assertEquals(60, saved(mentee.getUsername()).getExperience(), "share + additive bonus");
        assertEquals(50, saved(mentor.getUsername()).getExperience(), "mentor's own share is unchanged");
    }

    @Test
    void menteeGraduatesEndingBondAndGrantingMentorTitle() {
        // Mentee is already at the graduation threshold for a level-20 mentor (min(20-10,20)=10), so
        // the first shared kill graduates them. The mentor lands the kill (mentee is a party member).
        Player mentor = leveled("Mentor", 20).withMentee("Mentee", 1000L);
        Player mentee = leveled("Mentee", 10).withMentor("Mentor", 1000L);
        MobRegistry registry = buildRegistry(goblin(20), mentor, mentee);

        GameActionResult result = registry.processPlayerAttack(mentor, "Goblin", ROOM_ID);

        // The mentor (killer) is told their mentee graduated and earns the title.
        List<String> mentorTexts = result.messages().stream().map(GameMessage::text).toList();
        assertTrue(mentorTexts.stream().anyMatch(t -> t.contains("Mentee") && t.contains("has graduated")),
            "Mentor should be told their mentee graduated: " + mentorTexts);
        assertTrue(mentorTexts.contains("Title earned: " + MentorService.MENTOR_TITLE + "!"),
            "Mentor should earn the mentor title on their first graduation: " + mentorTexts);
        // The mentee is told they graduated.
        assertTrue(capturedTexts(mentee.getUsername()).stream()
                .anyMatch(t -> t.contains("You have graduated")),
            "Mentee should be told they graduated: " + capturedTexts(mentee.getUsername()));

        Player savedMentor = saved(mentor.getUsername());
        Player savedMentee = saved(mentee.getUsername());
        assertNull(savedMentor.mentee(), "mentor's bond is cleared");
        assertNull(savedMentee.mentor(), "mentee's bond is cleared");
        assertEquals(1, savedMentor.menteesGraduated(), "graduation counter incremented once");
        assertTrue(savedMentor.titles().has(MentorService.MENTOR_TITLE), "mentor title persisted");
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
        public Optional<AttackDefinition> findById(AttackId id) {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private record StubItemRepository(Map<ItemId, Item> items) implements ItemRepository {
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

        StubPlayerRepository(List<Player> initial) {
            for (Player p : initial) {
                store.put(p.getUsername(), p);
            }
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
            this.room = new Room(roomId, "Test Room", "A featureless void.",
                Map.of(), List.of(), List.of());
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
