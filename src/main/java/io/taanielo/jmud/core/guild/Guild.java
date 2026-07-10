package io.taanielo.jmud.core.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 */
public record Guild(GuildId id, String name, Username leaderId, List<GuildMember> members, int treasuryGold) {

    public Guild {
        Objects.requireNonNull(id, "Guild id is required");
        Objects.requireNonNull(name, "Guild name is required");
        Objects.requireNonNull(leaderId, "Guild leaderId is required");
        members = List.copyOf(Objects.requireNonNull(members, "Guild members are required"));
        if (treasuryGold < 0) {
            throw new IllegalArgumentException("Guild treasuryGold must not be negative");
        }
    }

    /**
     * Founds a new guild with {@code leader} as its sole member and leader and an empty treasury.
     *
     * @param id     the new guild's id
     * @param name   the guild's display name
     * @param leader the founding player
     * @return the newly created guild
     */
    public static Guild found(GuildId id, String name, Username leader) {
        return new Guild(id, name, leader, List.of(new GuildMember(leader, GuildRank.LEADER, 0)), 0);
    }

    /** Returns {@code true} when the given player is the guild leader. */
    public boolean isLeader(Username username) {
        return leaderId.equals(username);
    }

    /** Returns {@code true} when the given player is a member of this guild. */
    public boolean isMember(Username username) {
        return members.stream().anyMatch(m -> m.username().equals(username));
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
        return new Guild(id, name, leaderId, next, treasuryGold);
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
            return new Guild(id, name, leaderId, List.of(), treasuryGold);
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
        return new Guild(id, name, newLeader, remaining, treasuryGold);
    }

    /**
     * Returns a copy of this guild whose treasury has been increased by {@code amount}.
     *
     * @param amount the non-negative amount of gold to add to the treasury
     * @return the updated guild
     * @throws IllegalArgumentException when {@code amount} is negative
     */
    public Guild depositTreasury(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Deposit amount must not be negative");
        }
        return new Guild(id, name, leaderId, members, treasuryGold + amount);
    }

    /**
     * Returns a copy of this guild whose treasury has been decreased by {@code amount}.
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
        return new Guild(id, name, leaderId, members, treasuryGold - amount);
    }
}
