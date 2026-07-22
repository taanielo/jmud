package io.taanielo.jmud.core.bounty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.MobTemplateRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link BountyExpiryTicker}: an expired bounty refunds the full stake to its poster
 * (online or offline) and mails a note, active bounties are untouched, and an unresolvable poster is
 * skipped — all without networking or file I/O (AGENTS.md §10).
 */
class BountyExpiryTickerTest {

    private static final long EXPIRY_TICKS = 100;

    private StubBountyRepository bountyRepo;
    private BountyService service;

    @BeforeEach
    void setUp() {
        bountyRepo = new StubBountyRepository();
        service = new BountyService(bountyRepo, new StubTemplateRepo(), new NoopBroadcaster(), 5);
    }

    @Test
    void tick_refundsExpiredBountyAndMailsPoster() {
        bountyRepo.stored.add(Bounty.onMob(Username.of("alice"), "mob.goblin", "Goblin", 100, 0L));
        Player poster = playerNamed("alice", 400);
        List<Player> persisted = new ArrayList<>();
        BountyExpiryTicker ticker = new BountyExpiryTicker(
            service, () -> EXPIRY_TICKS, () -> EXPIRY_TICKS,
            username -> Optional.of(poster), persisted::add);

        ticker.tick();

        assertEquals(1, persisted.size(), "the poster is persisted once");
        Player updated = persisted.get(0);
        assertEquals(500, updated.getGold(), "the full stake is refunded");
        assertTrue(updated.mailbox().messages().get(0).body().contains("expired"), "a note is mailed");
        assertTrue(bountyRepo.stored.isEmpty(), "the expired bounty is removed");
    }

    @Test
    void tick_doesNothing_whenNoBountiesExpired() {
        bountyRepo.stored.add(Bounty.onMob(Username.of("alice"), "mob.goblin", "Goblin", 100, 0L));
        List<Player> persisted = new ArrayList<>();
        BountyExpiryTicker ticker = new BountyExpiryTicker(
            service, () -> 50L, () -> EXPIRY_TICKS,
            username -> Optional.of(playerNamed("alice", 400)), persisted::add);

        ticker.tick();

        assertTrue(persisted.isEmpty(), "nothing is persisted while the bounty is active");
        assertEquals(1, bountyRepo.stored.size());
    }

    @Test
    void tick_skipsBounty_whenPosterCannotBeResolved() {
        bountyRepo.stored.add(Bounty.onMob(Username.of("ghost"), "mob.goblin", "Goblin", 100, 0L));
        List<Player> persisted = new ArrayList<>();
        BountyExpiryTicker ticker = new BountyExpiryTicker(
            service, () -> EXPIRY_TICKS, () -> EXPIRY_TICKS,
            username -> Optional.empty(), persisted::add);

        ticker.tick();

        assertTrue(persisted.isEmpty(), "an unresolvable poster cannot be refunded");
        assertTrue(bountyRepo.stored.isEmpty(), "the expired bounty was still removed");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static Player playerNamed(String name, int gold) {
        User user = User.of(Username.of(name), Password.hash("pass", 1));
        return Player.of(user, "{hp}hp>").addGold(gold);
    }

    private static final class StubBountyRepository implements BountyRepository {
        private final List<Bounty> stored = new ArrayList<>();

        @Override
        public List<Bounty> findAll() {
            return List.copyOf(stored);
        }

        @Override
        public void save(List<Bounty> updated) {
            stored.clear();
            stored.addAll(updated);
        }
    }

    private static final class StubTemplateRepo implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() {
            return List.of();
        }
    }

    private static final class NoopBroadcaster implements MessageBroadcaster {
        @Override
        public void sendToPlayer(Username target, Message message) {
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
        }
    }
}
