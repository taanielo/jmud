package io.taanielo.jmud.core.party;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Represents an active player party as an immutable snapshot.
 *
 * <p>A party always has at least one member (the leader). When membership
 * drops to one player the party is typically disbanded by {@link PartyService}.
 *
 * <p>The party also carries its {@link LootMode} and a {@code lootCursor} — the rotation pointer
 * used by {@link LootMode#ROUND_ROBIN} to remember which member is next in line for a dropped item
 * across successive kills. Both are transient, in-memory state consistent with the party itself.
 */
public record Party(Username leaderId, List<Username> memberIds, LootMode lootMode, int lootCursor) {

    /**
     * Constructs a party snapshot, defaulting a null {@code lootMode} to {@link LootMode#FREE} and
     * clamping a negative {@code lootCursor} to zero.
     *
     * @param leaderId   the username of the party leader
     * @param memberIds  all current member usernames (must include the leader)
     * @param lootMode   the loot-assignment mode (defaults to {@link LootMode#FREE} when null)
     * @param lootCursor the round-robin rotation pointer
     */
    public Party {
        Objects.requireNonNull(leaderId, "leaderId is required");
        Objects.requireNonNull(memberIds, "memberIds is required");
        memberIds = List.copyOf(memberIds);
        if (lootMode == null) {
            lootMode = LootMode.FREE;
        }
        if (lootCursor < 0) {
            lootCursor = 0;
        }
    }

    /**
     * Constructs a party with the given leader and member list, defaulting to {@link LootMode#FREE}
     * with the rotation pointer at the start.
     *
     * @param leaderId  the username of the party leader
     * @param memberIds all current member usernames (must include the leader)
     */
    public Party(Username leaderId, List<Username> memberIds) {
        this(leaderId, memberIds, LootMode.FREE, 0);
    }

    /**
     * Returns a copy of this party with a new leader and member list, preserving the current loot
     * mode and rotation pointer.
     *
     * @param newLeaderId the new leader username
     * @param newMemberIds the new member list
     * @return the updated party
     */
    public Party withMembers(Username newLeaderId, List<Username> newMemberIds) {
        return new Party(newLeaderId, newMemberIds, lootMode, lootCursor);
    }

    /**
     * Returns a copy of this party with the given loot mode, resetting the rotation pointer to the
     * start so a freshly enabled round-robin begins with the first eligible member.
     *
     * @param newLootMode the loot mode to apply
     * @return the updated party
     */
    public Party withLootMode(LootMode newLootMode) {
        return new Party(leaderId, memberIds, newLootMode, 0);
    }

    /**
     * Returns a copy of this party with the given rotation pointer.
     *
     * @param newLootCursor the new round-robin rotation pointer
     * @return the updated party
     */
    public Party withLootCursor(int newLootCursor) {
        return new Party(leaderId, memberIds, lootMode, newLootCursor);
    }

    /**
     * Returns {@code true} when the given username is the party leader.
     *
     * @param username the username to check
     * @return whether the player is the leader
     */
    public boolean isLeader(Username username) {
        return leaderId.equals(username);
    }

    /**
     * Returns {@code true} when the given username is a member of the party.
     *
     * @param username the username to check
     * @return whether the player is a member
     */
    public boolean contains(Username username) {
        return memberIds.contains(username);
    }
}
