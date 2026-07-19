package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.bounty.Bounty;
import io.taanielo.jmud.core.bounty.BountyRepository;
import io.taanielo.jmud.core.bounty.BountyService;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Verifies the full bounty happy path through the mob-death reward path: a player posts a bounty on a
 * mob type, and when another player kills that type the pooled reward is paid to the killer (split
 * across the eligible party, remainder to the killer so escrowed gold is conserved), a server-wide
 * announcement names the killer/mob/total, and the paid bounties close.
 */
class MobRegistryBountyPayoutTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());
    private static final String GOBLIN_ID = "mob.goblin";

    private final Map<Username, List<GameActionResult>> captured = new ConcurrentHashMap<>();
    private final InMemoryBountyRepository bountyRepo = new InMemoryBountyRepository();
    private final CapturingBroadcaster broadcaster = new CapturingBroadcaster();
    private StubPlayerRepository playerRepo;
    private PersistenceQueue queue;
    private BountyService bountyService;

    private Player player(String name) {
        return Player.of(User.of(Username.of(name), Password.hash("pw", 1)), "%hp> ");
    }

    /** A one-HP goblin that drops no gold, so any credited gold comes purely from the bounty. */
    private MobTemplate goblin() {
        return new MobTemplate(
            MobId.of(GOBLIN_ID), "Goblin", 1, DEFAULT_ATTACK, null, false,
            List.of(), ROOM_ID, 1, 10, 5, null, List.of(), false);
    }

    private MobRegistry buildRegistry(boolean partied, Player... players) {
        MobTemplate template = goblin();
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

        bountyService = new BountyService(bountyRepo, templateRepo, broadcaster, 5);
        registry.setBountyService(bountyService);

        if (partied) {
            PartyService partyService = new PartyService();
            Username leader = players[0].getUsername();
            partyService.form(leader);
            for (int i = 1; i < players.length; i++) {
                partyService.invite(leader, players[i].getUsername(), true);
                partyService.accept(players[i].getUsername());
            }
            registry.setPartyService(partyService);
        }
        registry.init();
        return registry;
    }

    private Player saved(Username username) {
        queue.flush(Duration.ofSeconds(5));
        return playerRepo.loadPlayer(username).orElseThrow();
    }

    private List<String> capturedTexts(Username username) {
        return captured.get(username).stream()
            .flatMap(r -> r.messages().stream())
            .map(GameMessage::text)
            .toList();
    }

    @Test
    void soloKiller_receivesFullPooledBounty_andServerWideAnnouncement() {
        // Alice (not in the room) funds a 200-gold bounty on goblins; Bob kills one.
        Player alice = player("Alice").addGold(200);
        Player bob = player("Bob");
        MobRegistry registry = buildRegistryWithSeededBounty(alice, 200, false, bob);

        GameActionResult result = registry.processPlayerAttack(bob, "Goblin", ROOM_ID);

        assertEquals(200, saved(bob.getUsername()).getGold(), "the killer collects the full bounty");
        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("You claim 200 gold in bounty for slaying the Goblin!")),
            "killer sees a bounty payout message: "
                + result.messages().stream().map(GameMessage::text).toList());
        assertEquals(1, broadcaster.global.size(), "exactly one server-wide announcement");
        String announcement = broadcaster.global.get(0);
        assertTrue(announcement.contains("Bob") && announcement.contains("Goblin")
                && announcement.contains("200"), announcement);
        assertTrue(bountyRepo.findAll().isEmpty(), "the paid bounty closes");
    }

    @Test
    void partyKill_splitsBountyAcrossMembers_withRemainderToKiller() {
        Player alice = player("Alice").addGold(100);
        Player bob = player("Bob");
        Player carol = player("Carol");
        Player dave = player("Dave");
        // Alice funds a 100-gold bounty; Bob/Carol/Dave (a party) kill the goblin together.
        MobRegistry registry = buildRegistryWithSeededBounty(alice, 100, true, bob, carol, dave);

        registry.processPlayerAttack(bob, "Goblin", ROOM_ID);

        // 100 / 3 = 33 each; the killer (Bob) absorbs the remaining 1 so the pool is paid in full.
        assertEquals(34, saved(bob.getUsername()).getGold(), "killer gets share + remainder");
        assertEquals(33, saved(carol.getUsername()).getGold());
        assertEquals(33, saved(dave.getUsername()).getGold());
        int total = saved(bob.getUsername()).getGold()
            + saved(carol.getUsername()).getGold()
            + saved(dave.getUsername()).getGold();
        assertEquals(100, total, "escrowed bounty gold is conserved — paid out in full, never lost");
        assertTrue(capturedTexts(carol.getUsername())
                .contains("You claim 33 gold in bounty for slaying the Goblin!"),
            "a party member sees their own bounty share message");
    }

    /**
     * Builds a registry after escrowing a real bounty from {@code funder} through
     * {@link BountyService#post} on the goblin type, so the seeded bounty exercises the same escrow
     * path a live {@code BOUNTY POST} would.
     */
    private MobRegistry buildRegistryWithSeededBounty(
        Player funder, int gold, boolean partied, Player... killers) {
        MobRegistry registry = buildRegistry(partied, killers);
        Player debited = bountyService.post(funder, "goblin", gold, 0L).updatedActor();
        assertEquals(funder.getGold() - gold, debited.getGold(), "post escrows the stake");
        return registry;
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private static final class InMemoryBountyRepository implements BountyRepository {
        private List<Bounty> bounties = List.of();

        @Override
        public List<Bounty> findAll() {
            return bounties;
        }

        @Override
        public void save(List<Bounty> updated) {
            this.bounties = List.copyOf(updated);
        }
    }

    private static final class CapturingBroadcaster implements MessageBroadcaster {
        private final List<String> global = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            global.add(((PlainTextMessage) message).text());
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
