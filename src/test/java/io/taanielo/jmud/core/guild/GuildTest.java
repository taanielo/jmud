package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;

class GuildTest {

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");
    private static final Username CAROL = Username.of("Carol");

    private static Item item(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A test item.", ItemAttributes.empty()).build();
    }

    @Test
    void foundedGuildHasLeaderAsSoleMember() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertTrue(guild.isLeader(ALICE));
        assertTrue(guild.isMember(ALICE));
        assertEquals(1, guild.memberCount());
        assertEquals(GuildRank.LEADER, guild.member(ALICE).orElseThrow().rank());
    }

    @Test
    void withMemberAppendsWithIncreasingJoinOrder() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .withMember(BOB)
            .withMember(CAROL);

        assertEquals(3, guild.memberCount());
        assertEquals(1, guild.member(BOB).orElseThrow().joinOrder());
        assertEquals(2, guild.member(CAROL).orElseThrow().joinOrder());
        assertEquals(GuildRank.MEMBER, guild.member(BOB).orElseThrow().rank());
    }

    @Test
    void withMemberIsIdempotentForExistingMember() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE).withMember(BOB);

        assertSame(guild, guild.withMember(BOB));
    }

    @Test
    void leaderLeavingTransfersToLongestTenuredMember() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .withMember(BOB)
            .withMember(CAROL);

        Guild after = guild.withoutMember(ALICE);

        assertFalse(after.isMember(ALICE));
        assertEquals(BOB, after.leaderId());
        assertEquals(GuildRank.LEADER, after.member(BOB).orElseThrow().rank());
        assertEquals(2, after.memberCount());
    }

    @Test
    void lastMemberLeavingEmptiesGuild() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        Guild after = guild.withoutMember(ALICE);

        assertEquals(0, after.memberCount());
    }

    @Test
    void nonLeaderLeavingKeepsLeader() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE).withMember(BOB);

        Guild after = guild.withoutMember(BOB);

        assertEquals(ALICE, after.leaderId());
        assertFalse(after.isMember(BOB));
    }

    @Test
    void removingNonMemberReturnsSameInstance() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertSame(guild, guild.withoutMember(BOB));
    }

    @Test
    void foundedGuildHasEmptyTreasury() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertEquals(0, guild.treasuryGold());
    }

    @Test
    void depositAndWithdrawAdjustTreasury() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .depositTreasury(100)
            .depositTreasury(50);

        assertEquals(150, guild.treasuryGold());

        Guild after = guild.withdrawTreasury(60);

        assertEquals(90, after.treasuryGold());
    }

    @Test
    void treasurySurvivesRosterChanges() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .depositTreasury(200)
            .withMember(BOB);

        assertEquals(200, guild.treasuryGold());
        assertEquals(200, guild.withoutMember(BOB).treasuryGold());
    }

    @Test
    void withdrawMoreThanBalanceThrows() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE).depositTreasury(10);

        assertThrows(IllegalArgumentException.class, () -> guild.withdrawTreasury(11));
    }

    @Test
    void negativeDepositThrows() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertThrows(IllegalArgumentException.class, () -> guild.depositTreasury(-1));
    }

    @Test
    void foundedGuildIsLevelOneWithNoLifetimeDeposits() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertEquals(0, guild.lifetimeDepositedGold());
        assertEquals(GuildLevel.ONE, guild.level());
        assertEquals(500, guild.nextLevelThreshold().getAsInt());
    }

    @Test
    void depositAccumulatesLifetimeGoldAndRaisesLevel() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .depositTreasury(300)
            .depositTreasury(300);

        assertEquals(600, guild.treasuryGold());
        assertEquals(600, guild.lifetimeDepositedGold());
        assertEquals(GuildLevel.TWO, guild.level());
        assertEquals(2_000, guild.nextLevelThreshold().getAsInt());
    }

    @Test
    void withdrawDoesNotReduceLifetimeGoldOrLevel() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .depositTreasury(600)
            .withdrawTreasury(600);

        assertEquals(0, guild.treasuryGold());
        assertEquals(600, guild.lifetimeDepositedGold());
        assertEquals(GuildLevel.TWO, guild.level());
    }

    @Test
    void maxLevelGuildHasNoNextThreshold() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .depositTreasury(15_000);

        assertEquals(GuildLevel.FIVE, guild.level());
        assertTrue(guild.nextLevelThreshold().isEmpty());
    }

    @Test
    void lifetimeGoldSurvivesRosterChanges() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .depositTreasury(700)
            .withMember(BOB);

        assertEquals(700, guild.withoutMember(BOB).lifetimeDepositedGold());
        assertEquals(700, guild.withMemberRank(BOB, GuildRank.OFFICER).lifetimeDepositedGold());
    }

    @Test
    void foundedGuildHasEmptyVault() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertTrue(guild.vaultedItems().isEmpty());
    }

    @Test
    void withVaultedItemAppendsEntry() {
        VaultedItem sword = new VaultedItem(item("sword", "a longsword"), ALICE);

        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE).withVaultedItem(sword);

        assertEquals(1, guild.vaultedItems().size());
        assertEquals(sword, guild.vaultedItems().get(0));
        assertEquals(ALICE, guild.vaultedItems().get(0).depositor());
    }

    @Test
    void withoutVaultedItemRemovesFirstMatch() {
        VaultedItem sword = new VaultedItem(item("sword", "a longsword"), ALICE);
        VaultedItem shield = new VaultedItem(item("shield", "a round shield"), BOB);
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .withVaultedItem(sword)
            .withVaultedItem(shield);

        Guild after = guild.withoutVaultedItem(sword);

        assertEquals(1, after.vaultedItems().size());
        assertEquals(shield, after.vaultedItems().get(0));
    }

    @Test
    void withoutVaultedItemForAbsentEntryReturnsSameInstance() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertSame(guild, guild.withoutVaultedItem(new VaultedItem(item("sword", "a longsword"), ALICE)));
    }

    @Test
    void vaultSurvivesRosterChanges() {
        VaultedItem sword = new VaultedItem(item("sword", "a longsword"), ALICE);
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .withVaultedItem(sword)
            .withMember(BOB);

        assertEquals(1, guild.vaultedItems().size());
        assertEquals(1, guild.withoutMember(BOB).vaultedItems().size());
    }

    @Test
    void dailyInterestIsLevelScaledFloorOfTreasury() {
        // Level 1 (1%): 1000 -> 10.
        Guild lvl1 = Guild.found(GuildId.of("g1"), "Ironclad", ALICE).creditTreasuryInterest(1_000);
        assertEquals(10, lvl1.dailyTreasuryInterest());

        // Odd balance floors down: 1099 * 1% = 10.99 -> 10.
        Guild odd = Guild.found(GuildId.of("g2"), "Oddments", ALICE).creditTreasuryInterest(1_099);
        assertEquals(10, odd.dailyTreasuryInterest());

        // Sub-threshold balance floors to zero: 50 * 1% = 0.5 -> 0.
        Guild tiny = Guild.found(GuildId.of("g3"), "Tiny", ALICE).creditTreasuryInterest(50);
        assertEquals(0, tiny.dailyTreasuryInterest());
    }

    @Test
    void dailyInterestUsesTheGuildsCurrentLevelRate() {
        // 5,000 lifetime gold -> level 4 (4%); interest is on the *current* treasury balance.
        Guild lvl4 = Guild.found(GuildId.of("g1"), "Ironclad", ALICE).depositTreasury(5_000);
        assertEquals(GuildLevel.FOUR, lvl4.level());
        assertEquals(200, lvl4.dailyTreasuryInterest());
    }

    @Test
    void emptyTreasuryEarnsNoInterest() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertEquals(0, guild.treasuryGold());
        assertEquals(0, guild.dailyTreasuryInterest());
    }

    @Test
    void creditingInterestRaisesTreasuryButNeverLifetimeGoldOrLevel() {
        // Level 2 (2%) guild funded by a 600-gold deposit; crediting interest must not level it up.
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE).depositTreasury(600);
        int before = guild.dailyTreasuryInterest();

        Guild credited = guild.creditTreasuryInterest(before);

        assertEquals(600 + before, credited.treasuryGold());
        assertEquals(600, credited.lifetimeDepositedGold());
        assertEquals(GuildLevel.TWO, credited.level());
    }

    @Test
    void repeatedInterestCreditsNeverAdvanceLevel() {
        // Even if interest compounds the treasury toward a higher threshold, the level cannot move,
        // because level is a pure function of lifetimeDepositedGold, which interest never touches.
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE).creditTreasuryInterest(400);
        for (int day = 0; day < 100; day++) {
            guild = guild.creditTreasuryInterest(guild.dailyTreasuryInterest());
        }

        assertEquals(0, guild.lifetimeDepositedGold());
        assertEquals(GuildLevel.ONE, guild.level());
    }

    @Test
    void negativeInterestThrows() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertThrows(IllegalArgumentException.class, () -> guild.creditTreasuryInterest(-1));
    }

    @Test
    void creditingInterestNearTheCapClampsInsteadOfOverflowingToNegative() {
        // A treasury a few gold below Integer.MAX_VALUE must not wrap to a negative balance (issue #785):
        // it clamps at the cap and stays a valid, positive, withdrawable balance.
        Guild guild = new Guild(
            GuildId.of("g1"), "Ironclad", ALICE,
            List.of(new GuildMember(ALICE, GuildRank.LEADER, 0)),
            Integer.MAX_VALUE - 5, List.of(), 0, null, null, 0);

        Guild credited = guild.creditTreasuryInterest(1_000);

        assertEquals(Guild.TREASURY_CAP, credited.treasuryGold());
        assertTrue(credited.treasuryGold() > 0);
    }

    @Test
    void depositingNearTheCapClampsBothTreasuryAndLifetimeGold() {
        Guild guild = new Guild(
            GuildId.of("g1"), "Ironclad", ALICE,
            List.of(new GuildMember(ALICE, GuildRank.LEADER, 0)),
            Integer.MAX_VALUE - 5, List.of(), Integer.MAX_VALUE - 5, null, null, 0);

        Guild deposited = guild.depositTreasury(1_000);

        assertEquals(Guild.TREASURY_CAP, deposited.treasuryGold());
        assertEquals(Guild.TREASURY_CAP, deposited.lifetimeDepositedGold());
        assertTrue(deposited.treasuryGold() > 0);
        assertTrue(deposited.lifetimeDepositedGold() > 0);
    }

    @Test
    void foundedGuildIsNotAtWarWithZeroWarWins() {
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE);

        assertFalse(guild.isAtWar());
        assertEquals(0, guild.warWins());
        assertNull(guild.activeWar());
    }

    @Test
    void withActiveWarAndWarWinAreAdditiveAndSurviveRosterChanges() {
        GuildWar war = GuildWar.against(GuildId.of("rival"));
        Guild guild = Guild.found(GuildId.of("g1"), "Ironclad", ALICE)
            .withActiveWar(war)
            .withWarWin()
            .withMember(BOB);

        assertTrue(guild.isAtWar());
        assertEquals(GuildId.of("rival"), guild.activeWar().opponent());
        assertEquals(1, guild.warWins());
        // Clearing the war leaves the win count untouched.
        Guild ended = guild.withActiveWar(null);
        assertNull(ended.activeWar());
        assertEquals(1, ended.warWins());
    }
}
