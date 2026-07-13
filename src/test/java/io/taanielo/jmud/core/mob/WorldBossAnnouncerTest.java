package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.guild.Guild;
import io.taanielo.jmud.core.guild.GuildId;
import io.taanielo.jmud.core.guild.GuildRepository;
import io.taanielo.jmud.core.guild.GuildRepositoryException;
import io.taanielo.jmud.core.guild.GuildService;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for {@link WorldBossAnnouncer}: spawn/death announcement content and the killer's
 * guild/party affiliation resolution.
 */
class WorldBossAnnouncerTest {

    private static final RoomId ROOM_ID = RoomId.of("frozen-peaks-summit");
    private static final String BOSS_NAME = "Vharixis the Frost Wyrm";

    @Test
    void announceSpawn_broadcastsGloballyWithBossAndRoomName() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldBossAnnouncer announcer = new WorldBossAnnouncer(
            broadcaster, roomService("Frozen Peaks Summit"), null, null);

        announcer.announceSpawn(BOSS_NAME, ROOM_ID);

        assertEquals(1, broadcaster.globals.size(), "Expected exactly one global broadcast");
        assertEquals(
            "The earth trembles — " + BOSS_NAME + " has awoken in the Frozen Peaks Summit!",
            broadcaster.lastGlobalText());
    }

    @Test
    void announceDeath_namesKillerAndGuildWhenInGuild() throws GuildRepositoryException {
        Username killer = Username.of("Grimtooth");
        GuildService guildService = new GuildService(new InMemoryGuildRepository());
        guildService.create(killer, "Ironclad Vanguard");

        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldBossAnnouncer announcer = new WorldBossAnnouncer(
            broadcaster, roomService("Summit"), guildService, new PartyService());

        announcer.announceDeath(BOSS_NAME, killer);

        assertEquals(
            BOSS_NAME + " has fallen to Grimtooth of the Ironclad Vanguard!",
            broadcaster.lastGlobalText());
    }

    @Test
    void announceDeath_namesPartyWhenGroupedButGuildless() {
        Username killer = Username.of("Grimtooth");
        PartyService partyService = new PartyService();
        partyService.form(killer);

        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldBossAnnouncer announcer = new WorldBossAnnouncer(
            broadcaster, roomService("Summit"), null, partyService);

        announcer.announceDeath(BOSS_NAME, killer);

        assertEquals(
            BOSS_NAME + " has fallen to Grimtooth and their party!",
            broadcaster.lastGlobalText());
    }

    @Test
    void announceDeath_prefersGuildOverParty() throws GuildRepositoryException {
        Username killer = Username.of("Grimtooth");
        GuildService guildService = new GuildService(new InMemoryGuildRepository());
        guildService.create(killer, "Ironclad Vanguard");
        PartyService partyService = new PartyService();
        partyService.form(killer);

        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldBossAnnouncer announcer = new WorldBossAnnouncer(
            broadcaster, roomService("Summit"), guildService, partyService);

        announcer.announceDeath(BOSS_NAME, killer);

        assertTrue(broadcaster.lastGlobalText().endsWith("of the Ironclad Vanguard!"),
            "Expected guild affiliation to take precedence, got: " + broadcaster.lastGlobalText());
    }

    @Test
    void announceDeath_plainWhenUnaffiliated() {
        Username killer = Username.of("Grimtooth");
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldBossAnnouncer announcer = new WorldBossAnnouncer(
            broadcaster, roomService("Summit"), emptyGuildService(), new PartyService());

        announcer.announceDeath(BOSS_NAME, killer);

        assertEquals(BOSS_NAME + " has fallen to Grimtooth!", broadcaster.lastGlobalText());
    }

    @Test
    void announceEventSpawn_broadcastsRiftOpeningWithMobAndRoom() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldBossAnnouncer announcer = new WorldBossAnnouncer(
            broadcaster, roomService("Glacier"), null, null);

        announcer.announceEventSpawn("the Rimewrought Stalker", ROOM_ID);

        assertEquals(
            "A crack of unnatural energy tears open in the Glacier — the Rimewrought Stalker has emerged!",
            broadcaster.lastGlobalText());
    }

    @Test
    void announceEventTimeout_broadcastsRiftCollapse() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldBossAnnouncer announcer = new WorldBossAnnouncer(
            broadcaster, roomService("Glacier"), null, null);

        announcer.announceEventTimeout("the Rimewrought Stalker", ROOM_ID);

        assertEquals(
            "The rift over the Glacier collapses — the Rimewrought Stalker fades away.",
            broadcaster.lastGlobalText());
    }

    // ── helpers ───────────────────────────────────────────────────────

    private RoomService roomService(String roomName) {
        return new RoomService(new StubRoomRepository(roomName), ROOM_ID);
    }

    private static GuildService emptyGuildService() {
        try {
            return new GuildService(new InMemoryGuildRepository());
        } catch (GuildRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class CapturingBroadcaster implements MessageBroadcaster {
        private final List<Message> globals = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            globals.add(message);
        }

        String lastGlobalText() {
            return ((PlainTextMessage) globals.get(globals.size() - 1)).text();
        }
    }

    private static final class InMemoryGuildRepository implements GuildRepository {
        private final List<Guild> saved = new ArrayList<>();

        @Override
        public List<Guild> loadAll() {
            return List.copyOf(saved);
        }

        @Override
        public void save(Guild guild) {
            saved.add(guild);
        }

        @Override
        public void delete(GuildId guildId) {
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        private final Room room;

        StubRoomRepository(String roomName) {
            this.room = new Room(ROOM_ID, roomName, "A frozen summit.", Map.of(), List.of(), List.of());
        }

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
