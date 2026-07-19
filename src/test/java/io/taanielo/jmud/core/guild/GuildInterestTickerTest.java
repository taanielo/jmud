package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.taanielo.jmud.core.world.WorldClock;

/**
 * Unit tests for {@link GuildInterestTicker}: a night-to-day transition credits each guild's treasury with
 * its level-scaled daily interest, never touching the lifetime-deposit counter that drives leveling.
 */
class GuildInterestTickerTest {

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");

    private WorldClock worldClock;
    private GuildService guildService;
    private RecordingBroadcaster broadcaster;
    private GuildInterestTicker ticker;

    @BeforeEach
    void setUp() throws Exception {
        worldClock = new WorldClock(1);
        guildService = new GuildService(new FakeGuildRepository());
        broadcaster = new RecordingBroadcaster();
        ticker = new GuildInterestTicker(worldClock, guildService, broadcaster);
        guildService.create(ALICE, "Ironclad");
    }

    private void advanceOneTick() {
        worldClock.tick();
        ticker.tick();
    }

    /**
     * Advances the clock by whole in-game days. With {@code ticksPerPhase = 1} a full day/night cycle (one
     * NIGHT->DAY transition) is two ticks, so {@code days} in-game days take {@code days * 2} ticks.
     */
    private void advanceGameDays(int days) {
        for (int i = 0; i < days * 2; i++) {
            advanceOneTick();
        }
    }

    /** Advances exactly one full interest period, which ends on a crediting boundary. */
    private void advanceOneInterestPeriod() {
        advanceGameDays(GuildInterestTicker.GAME_DAYS_PER_INTEREST_PERIOD);
    }

    private Guild ironclad() {
        return guildService.guildOf(ALICE).orElseThrow();
    }

    @Test
    void doesNotCreditOnDayToNightTransition() {
        guildService.deposit(ALICE, 1_000); // level 2 (2%)
        advanceOneTick(); // DAY -> NIGHT, not a new day

        assertEquals(1_000, ironclad().treasuryGold());
        assertTrue(broadcaster.messages.isEmpty());
    }

    @Test
    void doesNotCreditEveryNightToDayFlip() {
        // A single in-game day (~100s at the default clock) must not trigger interest: the runaway
        // ~100-second faucet of issue #785 is gone. Interest lands only once a full period elapses.
        guildService.deposit(ALICE, 1_000); // level 2 (2%)

        advanceGameDays(GuildInterestTicker.GAME_DAYS_PER_INTEREST_PERIOD - 1);

        assertEquals(1_000, ironclad().treasuryGold());
        assertTrue(broadcaster.messages.isEmpty());
    }

    @Test
    void creditsLevelScaledInterestOncePerInterestPeriod() {
        guildService.deposit(ALICE, 1_000); // level 2 (2%)

        advanceOneInterestPeriod();

        assertEquals(1_020, ironclad().treasuryGold());
    }

    @Test
    void interestNeverChangesLifetimeGoldOrLevel() {
        guildService.deposit(ALICE, 1_000); // level 2 (2%)
        int lifetimeBefore = ironclad().lifetimeDepositedGold();
        GuildLevel levelBefore = ironclad().level();

        advanceOneInterestPeriod();

        assertEquals(lifetimeBefore, ironclad().lifetimeDepositedGold());
        assertEquals(levelBefore, ironclad().level());
    }

    @Test
    void zeroBalanceGuildIsSkippedWithNoAnnouncement() {
        // No deposit: the treasury is empty, so no interest and no [Guild] spam.
        advanceOneInterestPeriod();

        assertEquals(0, ironclad().treasuryGold());
        assertTrue(broadcaster.messages.isEmpty());
    }

    @Test
    void announcesInterestToEveryMember() {
        guildService.accept(inviteBob());
        guildService.deposit(ALICE, 1_000); // level 2 (2%) -> 20 interest

        advanceOneInterestPeriod();

        assertEquals(2, broadcaster.recipients.size());
        assertTrue(broadcaster.recipients.contains(ALICE));
        assertTrue(broadcaster.recipients.contains(BOB));
        assertTrue(broadcaster.messages.stream()
            .anyMatch(m -> m.contains("20 gold") && m.contains("2%")));
    }

    @Test
    void subThresholdBalanceEarnsNothingAndIsSilent() {
        guildService.deposit(ALICE, 50); // level 1 (1%): 50 * 1% floors to 0

        advanceOneInterestPeriod();

        assertEquals(50, ironclad().treasuryGold());
        assertTrue(broadcaster.messages.isEmpty());
    }

    private Username inviteBob() {
        guildService.invite(ALICE, BOB, true);
        return BOB;
    }

    private static final class RecordingBroadcaster implements MessageBroadcaster {
        private final List<String> messages = new ArrayList<>();
        private final List<Username> recipients = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
            recipients.add(target);
            messages.add(((PlainTextMessage) message).text());
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
        }
    }

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
