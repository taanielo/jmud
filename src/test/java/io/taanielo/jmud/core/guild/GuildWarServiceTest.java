package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link GuildWarService}: propose/accept/decline/timeout, war-point scoring
 * (correct, same-guild, third-party, ex-member), the auto-win condition, and concede.
 */
class GuildWarServiceTest {

    private static final Username ALICE = Username.of("Alice"); // leader of Ironclad
    private static final Username BOB = Username.of("Bob");     // leader of Redhand
    private static final Username CAROL = Username.of("Carol"); // leader of Emerald
    private static final Username EVE = Username.of("Eve");     // ordinary member of Ironclad
    private static final Username DAVE = Username.of("Dave");   // unguilded

    private GuildService guildService;
    private RecordingBroadcaster broadcaster;
    private GuildWarService service;

    @BeforeEach
    void setUp() throws Exception {
        guildService = new GuildService(new FakeGuildRepository());
        broadcaster = new RecordingBroadcaster();
        service = new GuildWarService(guildService, broadcaster);
        guildService.create(ALICE, "Ironclad");
        guildService.create(BOB, "Redhand");
    }

    // ── propose / accept / decline / timeout ───────────────────────────

    @Test
    void proposeNotifiesTargetLeaderAndRecordsPending() {
        GuildResult result = service.propose(ALICE, "Redhand");

        assertTrue(result.success());
        assertTrue(broadcaster.textsFor(BOB).stream().anyMatch(m -> m.contains("declared war")));
        // No war is active until accepted.
        assertFalse(guildService.guildOf(ALICE).orElseThrow().isAtWar());
    }

    @Test
    void onlyLeaderMayPropose() {
        guildService.invite(ALICE, EVE, true);
        guildService.accept(EVE);

        GuildResult result = service.propose(EVE, "Redhand");

        assertFalse(result.success());
        assertTrue(result.message().contains("Only the guild leader"));
    }

    @Test
    void proposeRejectedWhenProposerAlreadyAtWar() {
        service.propose(ALICE, "Redhand");
        service.accept(BOB);
        guildService.create(CAROL, "Emerald");

        GuildResult result = service.propose(ALICE, "Emerald");

        assertFalse(result.success());
        assertTrue(result.message().contains("already at war"));
    }

    @Test
    void proposeRejectedWhenTargetAlreadyAtWar() {
        guildService.create(CAROL, "Emerald");
        service.propose(ALICE, "Redhand");
        service.accept(BOB); // Ironclad vs Redhand active

        GuildResult result = service.propose(CAROL, "Redhand");

        assertFalse(result.success());
        assertTrue(result.message().contains("already at war"));
    }

    @Test
    void acceptStartsWarOnBothGuildsAndAnnouncesGlobally() {
        service.propose(ALICE, "Redhand");

        GuildResult result = service.accept(BOB);

        assertTrue(result.success());
        Guild ironclad = guildService.guildOf(ALICE).orElseThrow();
        Guild redhand = guildService.guildOf(BOB).orElseThrow();
        assertTrue(ironclad.isAtWar());
        assertTrue(redhand.isAtWar());
        assertEquals(redhand.id(), ironclad.activeWar().opponent());
        assertEquals(ironclad.id(), redhand.activeWar().opponent());
        assertTrue(broadcaster.globalTexts().stream().anyMatch(m -> m.contains("War is declared")));
    }

    @Test
    void acceptWithoutPendingProposalFails() {
        GuildResult result = service.accept(BOB);

        assertFalse(result.success());
        assertTrue(result.message().contains("no pending war declaration"));
    }

    @Test
    void declineRemovesPendingAndNotifiesProposer() {
        service.propose(ALICE, "Redhand");

        GuildResult result = service.decline(BOB);

        assertTrue(result.success());
        assertTrue(broadcaster.textsFor(ALICE).stream().anyMatch(m -> m.contains("declined")));
        // A declined proposal cannot then be accepted.
        assertFalse(service.accept(BOB).success());
    }

    @Test
    void proposalExpiresAfterTimeoutWindow() {
        service.propose(ALICE, "Redhand");

        for (int i = 0; i < GuildWarService.PROPOSAL_TIMEOUT_TICKS; i++) {
            service.tick();
        }

        GuildResult result = service.accept(BOB);
        assertFalse(result.success(), "proposal must expire after the timeout window");
    }

    // ── war-point scoring ──────────────────────────────────────────────

    @Test
    void duelWinBetweenWarringGuildsScoresForWinner() {
        startWar();

        service.recordDuelWin(ALICE, BOB);

        Guild ironclad = guildService.guildOf(ALICE).orElseThrow();
        Guild redhand = guildService.guildOf(BOB).orElseThrow();
        assertEquals(1, ironclad.activeWar().ownPoints());
        assertEquals(1, redhand.activeWar().opponentPoints());
        assertEquals(0, redhand.activeWar().ownPoints());
    }

    @Test
    void sameGuildDuelNeverScores() {
        guildService.invite(ALICE, EVE, true);
        guildService.accept(EVE);
        startWar();

        service.recordDuelWin(ALICE, EVE); // both in Ironclad

        assertEquals(0, guildService.guildOf(ALICE).orElseThrow().activeWar().ownPoints());
    }

    @Test
    void duelAgainstNonWarringThirdPartyNeverScores() {
        guildService.create(CAROL, "Emerald");
        startWar(); // Ironclad vs Redhand only

        service.recordDuelWin(ALICE, CAROL); // Emerald is not at war with Ironclad

        assertEquals(0, guildService.guildOf(ALICE).orElseThrow().activeWar().ownPoints());
    }

    @Test
    void duelAgainstUnguildedPlayerNeverScores() {
        startWar();

        service.recordDuelWin(ALICE, DAVE); // Dave is in no guild

        assertEquals(0, guildService.guildOf(ALICE).orElseThrow().activeWar().ownPoints());
    }

    @Test
    void exMemberWhoLeftDoesNotScoreForOldGuild() {
        guildService.invite(ALICE, EVE, true);
        guildService.accept(EVE);
        startWar();
        guildService.leave(EVE); // Eve leaves Ironclad after the war was declared

        service.recordDuelWin(EVE, BOB);

        assertEquals(0, guildService.guildOf(ALICE).orElseThrow().activeWar().ownPoints(),
            "membership is checked live at resolution, not snapshotted at declaration");
    }

    // ── win condition & concede ────────────────────────────────────────

    @Test
    void reachingFivePointsWinsTheWarAndIncrementsWarWins() {
        startWar();
        broadcaster.globalMessages.clear();

        for (int i = 0; i < GuildWar.POINTS_TO_WIN; i++) {
            service.recordDuelWin(ALICE, BOB);
        }

        Guild ironclad = guildService.guildOf(ALICE).orElseThrow();
        Guild redhand = guildService.guildOf(BOB).orElseThrow();
        assertNull(ironclad.activeWar(), "war ends on the winning point");
        assertNull(redhand.activeWar());
        assertEquals(1, ironclad.warWins());
        assertEquals(0, redhand.warWins());
        assertTrue(broadcaster.globalTexts().stream()
            .anyMatch(m -> m.contains("have triumphed over") && m.contains("Ironclad")));
    }

    @Test
    void concedeEndsWarEarlyAndCreditsTheOpponent() {
        startWar();

        GuildResult result = service.concede(ALICE);

        assertTrue(result.success());
        Guild ironclad = guildService.guildOf(ALICE).orElseThrow();
        Guild redhand = guildService.guildOf(BOB).orElseThrow();
        assertNull(ironclad.activeWar());
        assertNull(redhand.activeWar());
        assertEquals(1, redhand.warWins(), "the other guild is credited the win");
        assertEquals(0, ironclad.warWins());
        assertTrue(broadcaster.globalTexts().stream().anyMatch(m -> m.contains("conceded")));
    }

    @Test
    void concedeWithoutActiveWarFails() {
        GuildResult result = service.concede(ALICE);

        assertFalse(result.success());
        assertTrue(result.message().contains("not at war"));
    }

    @Test
    void statusShowsOpponentScoreAndTarget() {
        startWar();
        service.recordDuelWin(ALICE, BOB);

        List<String> lines = service.statusLines(ALICE);

        assertTrue(lines.stream().anyMatch(l -> l.contains("Ironclad") && l.contains("Redhand")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("1 war point")));
        assertTrue(lines.stream().anyMatch(l -> l.contains(String.valueOf(GuildWar.POINTS_TO_WIN))));
    }

    private void startWar() {
        service.propose(ALICE, "Redhand");
        service.accept(BOB);
    }

    /** Records delivered per-recipient and global messages for assertions. */
    private static final class RecordingBroadcaster implements MessageBroadcaster {
        private final List<Map.Entry<Username, String>> messages = new ArrayList<>();
        private final List<String> globalMessages = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
            messages.add(Map.entry(target, ((PlainTextMessage) message).text()));
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            globalMessages.add(((PlainTextMessage) message).text());
        }

        List<String> textsFor(Username user) {
            return messages.stream().filter(e -> e.getKey().equals(user)).map(Map.Entry::getValue).toList();
        }

        List<String> globalTexts() {
            return List.copyOf(globalMessages);
        }
    }

    /** In-memory {@link GuildRepository} mirroring the write-behind repository synchronously. */
    private static final class FakeGuildRepository implements GuildRepository {
        private final Map<GuildId, Guild> saved = new ConcurrentHashMap<>();

        @Override
        public List<Guild> loadAll() {
            return List.copyOf(saved.values());
        }

        @Override
        public void save(Guild guild) {
            saved.put(guild.id(), guild);
        }

        @Override
        public void delete(GuildId guildId) {
            saved.remove(guildId);
        }
    }
}
