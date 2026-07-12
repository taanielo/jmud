package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
