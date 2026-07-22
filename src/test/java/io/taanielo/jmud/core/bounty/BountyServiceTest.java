package io.taanielo.jmud.core.bounty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.mob.GoldDrop;
import io.taanielo.jmud.core.mob.MobId;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.MobTemplateRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link BountyService}: posting escrows gold and stacks backers, cancelling refunds
 * in full, listing aggregates per mob type, claiming pays out and announces, and non-combatant mobs
 * are rejected — all without networking (AGENTS.md §10).
 */
class BountyServiceTest {

    private static final RoomId ROOM = RoomId.of("room.test");
    private static final String GOBLIN_ID = "mob.goblin";

    private final InMemoryBountyRepository bountyRepo = new InMemoryBountyRepository();
    private final CapturingBroadcaster broadcaster = new CapturingBroadcaster();

    private static final int MAX_OPEN = 5;
    private static final long EXPIRY_TICKS = 6000;

    private BountyService service(MobTemplate... templates) {
        return service(MAX_OPEN, templates);
    }

    private BountyService service(int maxOpen, MobTemplate... templates) {
        return new BountyService(bountyRepo, new StubTemplateRepo(List.of(templates)), broadcaster, maxOpen);
    }

    private Player player(String name, int gold) {
        Player base = Player.of(User.of(Username.of(name), Password.hash("pw", 1)), "%hp> ");
        return base.addGold(gold);
    }

    private MobTemplate combatMob(String id, String name) {
        return new MobTemplate(
            MobId.of(id), name, 10, AttackId.of("attack.bite"), null, false,
            List.of(), ROOM, 1, 10, 5, new GoldDrop(1, 1), List.of(), false);
    }

    private MobTemplate peacefulMob(String id, String name) {
        return new MobTemplate(
            MobId.of(id), name, 10, null, null, false,
            List.of(), ROOM, 1, 10, 5, null, List.of(), false);
    }

    @Test
    void post_escrowsGoldAndCreatesBounty() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        Player poster = player("Alice", 500);

        BountyResult result = service.post(poster, "goblin", 100, 10);

        assertTrue(result.success(), result.message());
        assertEquals(400, result.updatedActor().getGold(), "stake is debited from the poster");
        assertEquals(1, bountyRepo.findAll().size());
        assertEquals(100, bountyRepo.findAll().get(0).reward());
        assertEquals(GOBLIN_ID, bountyRepo.findAll().get(0).targetId());
        assertTrue(bountyRepo.findAll().get(0).targetsMob());
    }

    @Test
    void post_rejectsNonCombatantMob() {
        BountyService service = service(peacefulMob("mob.baker", "Baker"));
        Player poster = player("Alice", 500);

        BountyResult result = service.post(poster, "baker", 100, 10);

        assertFalse(result.success());
        assertTrue(bountyRepo.findAll().isEmpty(), "no bounty is created against a harmless mob");
    }

    @Test
    void post_rejectsUnaffordableStake() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        Player poster = player("Alice", 50);

        BountyResult result = service.post(poster, "goblin", 100, 10);

        assertFalse(result.success());
        assertTrue(bountyRepo.findAll().isEmpty());
    }

    @Test
    void post_rejectsDuplicateFromSamePosterOnSameMob() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        Player poster = player("Alice", 500);

        Player afterFirst = service.post(poster, "goblin", 100, 10).updatedActor();
        BountyResult second = service.post(afterFirst, "goblin", 50, 12);

        assertFalse(second.success());
        assertEquals(1, bountyRepo.findAll().size(), "a poster keeps at most one bounty per mob type");
    }

    @Test
    void post_secondBackerStacksReward() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        service.post(player("Alice", 500), "goblin", 100, 10);
        service.post(player("Bob", 500), "goblin", 250, 20);

        List<BountyListing> listings = service.listings(30, EXPIRY_TICKS);
        assertEquals(1, listings.size());
        assertEquals(350, listings.get(0).totalReward(), "reward pools across backers");
        assertEquals(2, listings.get(0).backerCount());
        assertEquals(20, listings.get(0).ageTicks(), "age tracks the oldest open stake (tick 10 → 30)");
        assertEquals(EXPIRY_TICKS - 20, listings.get(0).remainingTicks(),
            "remaining tracks the soonest-to-lapse (oldest) stake");
    }

    @Test
    void post_rejectsOncePosterHitsOpenBountyCap() {
        BountyService service = service(2,
            combatMob("mob.goblin", "Goblin"),
            combatMob("mob.rat", "Rat"),
            combatMob("mob.orc", "Orc"));
        Player poster = player("Alice", 1000);

        Player afterFirst = service.post(poster, "goblin", 100, 10).updatedActor();
        Player afterSecond = service.post(afterFirst, "rat", 100, 11).updatedActor();
        BountyResult third = service.post(afterSecond, "orc", 100, 12);

        assertFalse(third.success(), "a post beyond the cap is rejected");
        assertTrue(third.message().contains("2"), third.message());
        assertTrue(third.message().contains("CANCEL"), third.message());
        assertEquals(2, bountyRepo.findAll().size(), "no bounty is created past the cap");
        assertEquals(800, afterSecond.getGold(), "no gold is moved by the rejected post");
    }

    @Test
    void expireBounties_removesStaleAndRefundsFullStake() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        Player poster = player("Alice", 500);
        Player afterPost = service.post(poster, "goblin", 100, 10).updatedActor();
        assertEquals(400, afterPost.getGold());

        long expiry = 100;
        List<Bounty> notYet = service.expireBounties(50, expiry);
        assertTrue(notYet.isEmpty(), "a fresh bounty does not expire");
        assertEquals(1, bountyRepo.findAll().size());

        List<Bounty> expired = service.expireBounties(10 + expiry, expiry);
        assertEquals(1, expired.size(), "the stale bounty expires");
        assertTrue(bountyRepo.findAll().isEmpty(), "the expired bounty is closed");

        Player refunded = service.applyExpiredRefund(afterPost, expired.get(0), 200);
        assertEquals(500, refunded.getGold(), "the full stake is refunded");
        assertFalse(refunded.mailbox().isEmpty(), "a refund note is mailed to the poster");
    }

    @Test
    void cancel_refundsPostersOwnStakeInFull() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        Player poster = player("Alice", 500);
        Player afterPost = service.post(poster, "goblin", 100, 10).updatedActor();
        assertEquals(400, afterPost.getGold());

        BountyResult cancel = service.cancel(afterPost, "goblin");

        assertTrue(cancel.success(), cancel.message());
        assertEquals(500, cancel.updatedActor().getGold(), "the full stake is refunded");
        assertTrue(bountyRepo.findAll().isEmpty(), "the cancelled bounty is closed");
    }

    @Test
    void cancel_rejectsWhenPosterHasNoMatchingBounty() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        Player poster = player("Alice", 500);

        BountyResult cancel = service.cancel(poster, "goblin");

        assertFalse(cancel.success());
    }

    @Test
    void claim_paysPooledTotalAndAnnouncesAndClosesEntries() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        service.post(player("Alice", 500), "goblin", 100, 10);
        service.post(player("Bob", 500), "goblin", 250, 12);

        int total = service.claim(GOBLIN_ID, Username.of("Carol"), "Goblin");

        assertEquals(350, total, "the killer earns the pooled reward");
        assertTrue(bountyRepo.findAll().isEmpty(), "paid entries close");
        assertEquals(1, broadcaster.global.size());
        String announcement = broadcaster.global.get(0);
        assertTrue(announcement.contains("Carol"), announcement);
        assertTrue(announcement.contains("Goblin"), announcement);
        assertTrue(announcement.contains("350"), announcement);
    }

    @Test
    void claim_onUnbountiedMobIsNoOp() {
        BountyService service = service(combatMob(GOBLIN_ID, "Goblin"));
        service.post(player("Alice", 500), "goblin", 100, 10);

        int total = service.claim("mob.rat", Username.of("Carol"), "Rat");

        assertEquals(0, total, "an un-bountied kill pays nothing");
        assertEquals(1, bountyRepo.findAll().size(), "unrelated bounties are untouched");
        assertTrue(broadcaster.global.isEmpty(), "no announcement on an un-bountied kill");
    }

    // ── player-target bounties (issue #807) ────────────────────────────

    @Test
    void postOnPlayer_escrowsGoldAndCreatesPlayerBounty() {
        BountyService service = service();
        Player poster = player("Alice", 500);

        BountyResult result = service.postOnPlayer(poster, Username.of("Grimjaw"), 150, 10);

        assertTrue(result.success(), result.message());
        assertEquals(350, result.updatedActor().getGold(), "stake is debited from the poster");
        assertEquals(1, bountyRepo.findAll().size());
        Bounty bounty = bountyRepo.findAll().get(0);
        assertTrue(bounty.targetsPlayer());
        assertEquals("Grimjaw", bounty.targetId());
        assertEquals(150, bounty.reward());
    }

    @Test
    void postOnPlayer_rejectsSelfTarget() {
        BountyService service = service();
        Player poster = player("Alice", 500);

        BountyResult result = service.postOnPlayer(poster, Username.of("alice"), 100, 10);

        assertFalse(result.success());
        assertTrue(bountyRepo.findAll().isEmpty(), "no bounty is created on oneself");
    }

    @Test
    void postOnPlayer_rejectsUnaffordableStake() {
        BountyService service = service();
        Player poster = player("Alice", 50);

        BountyResult result = service.postOnPlayer(poster, Username.of("Grimjaw"), 100, 10);

        assertFalse(result.success());
        assertTrue(bountyRepo.findAll().isEmpty());
    }

    @Test
    void postOnPlayer_secondBackerStacksReward() {
        BountyService service = service();
        service.postOnPlayer(player("Alice", 500), Username.of("Grimjaw"), 100, 10);
        service.postOnPlayer(player("Bob", 500), Username.of("Grimjaw"), 250, 20);

        List<BountyListing> listings = service.listings(30, EXPIRY_TICKS);
        assertEquals(1, listings.size());
        assertEquals(BountyTargetKind.PLAYER, listings.get(0).targetKind());
        assertEquals("Grimjaw", listings.get(0).targetName());
        assertEquals(350, listings.get(0).totalReward(), "reward pools across backers");
        assertEquals(2, listings.get(0).backerCount());
    }

    @Test
    void claimPlayerBounty_paysWinnerAndAnnouncesAndClosesEntries() {
        BountyService service = service();
        service.postOnPlayer(player("Alice", 500), Username.of("Grimjaw"), 100, 10);
        service.postOnPlayer(player("Bob", 500), Username.of("Grimjaw"), 250, 12);

        int total = service.claimPlayerBounty(Username.of("Grimjaw"), Username.of("Hero"));

        assertEquals(350, total, "the winner earns the full pooled reward");
        assertTrue(bountyRepo.findAll().isEmpty(), "paid entries close");
        assertEquals(1, broadcaster.global.size());
        String announcement = broadcaster.global.get(0);
        assertTrue(announcement.contains("Hero"), announcement);
        assertTrue(announcement.contains("Grimjaw"), announcement);
        assertTrue(announcement.contains("350"), announcement);
    }

    @Test
    void claimPlayerBounty_onUnbountiedLoserIsNoOp() {
        BountyService service = service();
        service.postOnPlayer(player("Alice", 500), Username.of("Grimjaw"), 100, 10);

        int total = service.claimPlayerBounty(Username.of("Innocent"), Username.of("Hero"));

        assertEquals(0, total, "an un-bountied duel pays nothing");
        assertEquals(1, bountyRepo.findAll().size(), "unrelated bounties are untouched");
        assertTrue(broadcaster.global.isEmpty(), "no announcement on an un-bountied duel");
    }

    @Test
    void claimPlayerBounty_ignoresMobBountiesWithSameName() {
        BountyService service = service(combatMob(GOBLIN_ID, "Grimjaw"));
        service.post(player("Alice", 500), "grimjaw", 100, 10);

        int total = service.claimPlayerBounty(Username.of("Grimjaw"), Username.of("Hero"));

        assertEquals(0, total, "a mob bounty is never claimed by a duel win");
        assertEquals(1, bountyRepo.findAll().size(), "the mob bounty is untouched");
    }

    @Test
    void cancel_refundsPlayerTargetStakeInFull() {
        BountyService service = service();
        Player poster = player("Alice", 500);
        Player afterPost = service.postOnPlayer(poster, Username.of("Grimjaw"), 120, 10).updatedActor();
        assertEquals(380, afterPost.getGold());

        BountyResult cancel = service.cancel(afterPost, "Grimjaw");

        assertTrue(cancel.success(), cancel.message());
        assertEquals(500, cancel.updatedActor().getGold(), "the full stake is refunded");
        assertTrue(bountyRepo.findAll().isEmpty(), "the cancelled bounty is closed");
    }

    @Test
    void expireBounties_refundsPlayerTargetStakeAndMails() {
        BountyService service = service();
        Player poster = player("Alice", 500);
        Player afterPost = service.postOnPlayer(poster, Username.of("Grimjaw"), 100, 10).updatedActor();

        long expiry = 100;
        List<Bounty> expired = service.expireBounties(10 + expiry, expiry);
        assertEquals(1, expired.size());

        Player refunded = service.applyExpiredRefund(afterPost, expired.get(0), 200);
        assertEquals(500, refunded.getGold(), "the full stake is refunded");
        assertFalse(refunded.mailbox().isEmpty(), "a refund note is mailed to the poster");
        assertTrue(refunded.mailbox().messages().get(0).body().contains("Grimjaw"),
            "the refund note names the player target");
    }

    // ── fakes ─────────────────────────────────────────────────────────

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

    private static final class StubTemplateRepo implements MobTemplateRepository {
        private final List<MobTemplate> templates;

        StubTemplateRepo(List<MobTemplate> templates) {
            this.templates = templates;
        }

        @Override
        public List<MobTemplate> findAll() {
            return templates;
        }
    }

    private static final class CapturingBroadcaster implements MessageBroadcaster {
        private final List<String> global = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
            // Not exercised by these tests.
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
            // Not exercised by these tests.
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            global.add(((PlainTextMessage) message).text());
        }
    }
}
