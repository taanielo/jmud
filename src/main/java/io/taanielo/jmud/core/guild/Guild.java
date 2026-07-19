package io.taanielo.jmud.core.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Immutable, persistent player guild: a named social unit with a leader and an ordered roster.
 *
 * <p>The roster is the authoritative record of who belongs to the guild. Every mutating operation
 * returns a new {@link Guild} instance; instances are never modified in place, so a snapshot handed
 * to another thread (e.g. the write-behind persistence worker) is always stable.
 *
 * <p>{@code treasuryGold} is the guild's shared bank balance: gold pooled by members and drawn only
 * by the leader. It is always non-negative and never created or destroyed by treasury operations —
 * every deposit/withdraw is matched by an equal, opposite change to a player's personal balance by
 * the caller.
 *
 * <p>{@code vaultedItems} is the guild's shared item vault: gear, materials and quest items pooled by
 * members. Like the treasury, items are moved rather than copied — every store/claim is matched by the
 * caller adding or removing the same item from a player's inventory, so no item is ever duplicated or
 * destroyed.
 *
 * <p>{@code lifetimeDepositedGold} is the running total of every gold piece ever paid into the treasury
 * via {@code GUILD DEPOSIT} over the guild's whole lifetime. It only ever increases — withdrawals do not
 * reduce it — and drives the guild's {@link #level() level}, which in turn scales the shared vault's
 * capacity. It is a "how much has this guild ever raised" counter, distinct from the current
 * {@code treasuryGold} balance.
 *
 * <p>{@code activeGuildQuest} is the guild's current cooperative {@link GuildQuest} — a shared "slay N of
 * a mob type" objective whose progress accrues from every member's kills. It is {@code null} for a guild
 * that has never been assigned one (e.g. a freshly loaded legacy guild); the {@link GuildQuestService}
 * lazily assigns one on first access, so no persistence migration is required.
 *
 * <p>{@code activeWar} is the guild's current {@link GuildWar} against a rival guild — a running tally of
 * consensual duel wins between the two guilds' members (issue #731). It is {@code null} when the guild is
 * not currently at war; a guild may only be in one war at a time (on either side). {@code warWins} is the
 * guild's persistent count of guild wars won over its lifetime. Both are additive: files written before
 * they existed load with no active war and zero wins, so no persistence migration is required.
 */
public record Guild(
    GuildId id,
    String name,
    Username leaderId,
    List<GuildMember> members,
    int treasuryGold,
    List<VaultedItem> vaultedItems,
    int lifetimeDepositedGold,
    @Nullable GuildQuest activeGuildQuest,
    @Nullable GuildWar activeWar,
    int warWins
) {

    public Guild {
        Objects.requireNonNull(id, "Guild id is required");
        Objects.requireNonNull(name, "Guild name is required");
        Objects.requireNonNull(leaderId, "Guild leaderId is required");
        members = List.copyOf(Objects.requireNonNull(members, "Guild members are required"));
        if (treasuryGold < 0) {
            throw new IllegalArgumentException("Guild treasuryGold must not be negative");
        }
        if (lifetimeDepositedGold < 0) {
            throw new IllegalArgumentException("Guild lifetimeDepositedGold must not be negative");
        }
        if (warWins < 0) {
            throw new IllegalArgumentException("Guild warWins must not be negative");
        }
        vaultedItems = List.copyOf(Objects.requireNonNull(vaultedItems, "Guild vaultedItems are required"));
    }

    /**
     * Founds a new guild with {@code leader} as its sole member and leader and an empty treasury and
     * vault.
     *
     * @param id     the new guild's id
     * @param name   the guild's display name
     * @param leader the founding player
     * @return the newly created guild
     */
    public static Guild found(GuildId id, String name, Username leader) {
        return new Guild(
            id, name, leader, List.of(new GuildMember(leader, GuildRank.LEADER, 0)), 0, List.of(), 0, null,
            null, 0);
    }

    /**
     * Returns the guild's current progression {@link GuildLevel}, derived purely from
     * {@link #lifetimeDepositedGold()}. Never stored; always recomputed on read.
     *
     * @return the guild's level (never below {@link GuildLevel#ONE})
     */
    public GuildLevel level() {
        return GuildLevel.forLifetimeGold(lifetimeDepositedGold);
    }

    /**
     * Returns the lifetime deposited gold required to reach the next {@link GuildLevel}, or empty when
     * the guild is already at the top level.
     *
     * @return the next level's threshold in lifetime gold, or empty at max level
     */
    public OptionalInt nextLevelThreshold() {
        return level().next()
            .map(next -> OptionalInt.of(next.threshold()))
            .orElseGet(OptionalInt::empty);
    }

    /** Returns {@code true} when the given player is the guild leader. */
    public boolean isLeader(Username username) {
        return leaderId.equals(username);
    }

    /** Returns {@code true} when the given player is a member of this guild. */
    public boolean isMember(Username username) {
        return members.stream().anyMatch(m -> m.username().equals(username));
    }

    /** Returns {@code true} when the given player is an officer of this guild. */
    public boolean isOfficer(Username username) {
        return member(username).map(m -> m.rank() == GuildRank.OFFICER).orElse(false);
    }

    /**
     * Returns {@code true} when the given player may moderate the guild (invite and kick members),
     * i.e. they are either the leader or an officer.
     */
    public boolean canModerate(Username username) {
        return isLeader(username) || isOfficer(username);
    }

    /** Returns the membership record for the given player, if present. */
    public Optional<GuildMember> member(Username username) {
        return members.stream().filter(m -> m.username().equals(username)).findFirst();
    }

    /** Returns the number of members currently in the guild. */
    public int memberCount() {
        return members.size();
    }

    /**
     * Returns a copy of this guild with {@code username} added as an ordinary member, or this
     * instance unchanged when the player is already a member. The new member's join order is one
     * greater than the current maximum, so it is always the most junior member.
     *
     * @param username the player joining the guild
     * @return the updated guild
     */
    public Guild withMember(Username username) {
        Objects.requireNonNull(username, "username is required");
        if (isMember(username)) {
            return this;
        }
        int nextOrder = members.stream().mapToInt(GuildMember::joinOrder).max().orElse(-1) + 1;
        List<GuildMember> next = new ArrayList<>(members);
        next.add(new GuildMember(username, GuildRank.MEMBER, nextOrder));
        return new Guild(
            id, name, leaderId, next, treasuryGold, vaultedItems, lifetimeDepositedGold, activeGuildQuest,
            activeWar, warWins);
    }

    /**
     * Returns a copy of this guild with {@code username} removed. When the departing player was the
     * leader and other members remain, leadership passes to the longest-tenured remaining member
     * (lowest join order). Returns this instance unchanged when the player was not a member.
     *
     * @param username the player leaving the guild
     * @return the updated guild; may have zero members if the last member left
     */
    public Guild withoutMember(Username username) {
        Objects.requireNonNull(username, "username is required");
        if (!isMember(username)) {
            return this;
        }
        List<GuildMember> remaining = new ArrayList<>(members.stream()
            .filter(m -> !m.username().equals(username))
            .toList());
        if (remaining.isEmpty()) {
            return new Guild(
                id, name, leaderId, List.of(), treasuryGold, vaultedItems, lifetimeDepositedGold,
                activeGuildQuest, activeWar, warWins);
        }
        Username newLeader = leaderId;
        if (leaderId.equals(username)) {
            GuildMember successor = remaining.stream()
                .min((a, b) -> Integer.compare(a.joinOrder(), b.joinOrder()))
                .orElseThrow();
            newLeader = successor.username();
            remaining = remaining.stream()
                .map(m -> m.username().equals(successor.username()) ? m.withRank(GuildRank.LEADER) : m)
                .toList();
        }
        return new Guild(
            id, name, newLeader, remaining, treasuryGold, vaultedItems, lifetimeDepositedGold,
            activeGuildQuest, activeWar, warWins);
    }

    /**
     * Returns a copy of this guild in which {@code username}'s rank has been set to {@code newRank}.
     * Returns this instance unchanged when the player is not a member or already holds that rank.
     *
     * @param username the member whose rank changes
     * @param newRank  the rank to assign
     * @return the updated guild
     */
    public Guild withMemberRank(Username username, GuildRank newRank) {
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(newRank, "newRank is required");
        Optional<GuildMember> current = member(username);
        if (current.isEmpty() || current.get().rank() == newRank) {
            return this;
        }
        List<GuildMember> next = members.stream()
            .map(m -> m.username().equals(username) ? m.withRank(newRank) : m)
            .toList();
        return new Guild(
            id, name, leaderId, next, treasuryGold, vaultedItems, lifetimeDepositedGold, activeGuildQuest,
            activeWar, warWins);
    }

    /**
     * Returns a copy of this guild whose treasury has been increased by {@code amount}. The same amount
     * is also added to {@link #lifetimeDepositedGold()}, which drives the guild's {@link #level() level}.
     *
     * @param amount the non-negative amount of gold to add to the treasury
     * @return the updated guild
     * @throws IllegalArgumentException when {@code amount} is negative
     */
    public Guild depositTreasury(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Deposit amount must not be negative");
        }
        return new Guild(
            id, name, leaderId, members, treasuryGold + amount, vaultedItems,
            lifetimeDepositedGold + amount, activeGuildQuest, activeWar, warWins);
    }

    /**
     * Returns the passive daily treasury interest this guild currently earns: the floor of its current
     * {@link #treasuryGold()} multiplied by its level's {@link GuildLevel#interestRatePercent()}. A guild
     * with an empty treasury earns nothing; the floor means odd balances round down to whole gold. The
     * multiplication is done in {@code long} arithmetic so a very large treasury never overflows.
     *
     * @return the interest amount, in whole gold, to credit this in-game day (never negative)
     */
    public int dailyTreasuryInterest() {
        return (int) ((long) treasuryGold * level().interestRatePercent() / 100);
    }

    /**
     * Returns a copy of this guild whose treasury has been increased by {@code interest} passive interest.
     *
     * <p>Unlike {@link #depositTreasury(int)}, this intentionally does <em>not</em> touch
     * {@link #lifetimeDepositedGold()}: interest is a reward for stewardship of banked gold, not a player
     * deposit, so it must never advance the guild's {@link #level() level}. Crediting it toward the
     * leveling counter would create a compounding interest→level→more-interest loop with no deposit
     * involved.
     *
     * @param interest the non-negative interest to add to the treasury
     * @return the updated guild
     * @throws IllegalArgumentException when {@code interest} is negative
     */
    public Guild creditTreasuryInterest(int interest) {
        if (interest < 0) {
            throw new IllegalArgumentException("Interest must not be negative");
        }
        return new Guild(
            id, name, leaderId, members, treasuryGold + interest, vaultedItems, lifetimeDepositedGold,
            activeGuildQuest, activeWar, warWins);
    }

    /**
     * Returns a copy of this guild whose treasury has been decreased by {@code amount}. Withdrawals
     * never affect {@link #lifetimeDepositedGold()}, so a guild's level can never drop.
     *
     * @param amount the non-negative amount of gold to remove from the treasury
     * @return the updated guild
     * @throws IllegalArgumentException when {@code amount} is negative or exceeds the balance
     */
    public Guild withdrawTreasury(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Withdraw amount must not be negative");
        }
        if (amount > treasuryGold) {
            throw new IllegalArgumentException("Withdraw amount exceeds treasury balance");
        }
        return new Guild(
            id, name, leaderId, members, treasuryGold - amount, vaultedItems, lifetimeDepositedGold,
            activeGuildQuest, activeWar, warWins);
    }

    /**
     * Returns a copy of this guild with {@code vaulted} appended to the shared item vault.
     *
     * @param vaulted the item (with its depositor) to add to the vault
     * @return the updated guild
     */
    public Guild withVaultedItem(VaultedItem vaulted) {
        Objects.requireNonNull(vaulted, "vaulted is required");
        List<VaultedItem> next = new ArrayList<>(vaultedItems);
        next.add(vaulted);
        return new Guild(
            id, name, leaderId, members, treasuryGold, next, lifetimeDepositedGold, activeGuildQuest,
            activeWar, warWins);
    }

    /**
     * Returns a copy of this guild with the first vault entry equal to {@code vaulted} removed, or this
     * instance unchanged when no such entry is present.
     *
     * @param vaulted the vault entry to remove
     * @return the updated guild
     */
    public Guild withoutVaultedItem(VaultedItem vaulted) {
        Objects.requireNonNull(vaulted, "vaulted is required");
        int index = vaultedItems.indexOf(vaulted);
        if (index < 0) {
            return this;
        }
        List<VaultedItem> next = new ArrayList<>(vaultedItems);
        next.remove(index);
        return new Guild(
            id, name, leaderId, members, treasuryGold, next, lifetimeDepositedGold, activeGuildQuest,
            activeWar, warWins);
    }

    /**
     * Returns a copy of this guild with its active cooperative guild quest set to {@code quest}. Passing
     * {@code null} clears the assignment (the {@link GuildQuestService} will lazily assign a fresh one on
     * next access).
     *
     * @param quest the new active guild quest, or {@code null} to clear it
     * @return the updated guild
     */
    public Guild withActiveGuildQuest(@Nullable GuildQuest quest) {
        return new Guild(
            id, name, leaderId, members, treasuryGold, vaultedItems, lifetimeDepositedGold, quest,
            activeWar, warWins);
    }

    /**
     * Returns a copy of this guild with its active guild war set to {@code war}. Passing {@code null}
     * clears the war (the guild is then free to declare or receive a new one).
     *
     * @param war the new active guild war, or {@code null} to clear it
     * @return the updated guild
     */
    public Guild withActiveWar(@Nullable GuildWar war) {
        return new Guild(
            id, name, leaderId, members, treasuryGold, vaultedItems, lifetimeDepositedGold, activeGuildQuest,
            war, warWins);
    }

    /**
     * Returns a copy of this guild with its persistent lifetime guild-war win count incremented by one.
     *
     * @return the updated guild
     */
    public Guild withWarWin() {
        return new Guild(
            id, name, leaderId, members, treasuryGold, vaultedItems, lifetimeDepositedGold, activeGuildQuest,
            activeWar, warWins + 1);
    }

    /** Returns {@code true} when this guild is currently engaged in a guild war. */
    public boolean isAtWar() {
        return activeWar != null;
    }
}
