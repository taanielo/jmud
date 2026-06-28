package io.taanielo.jmud.core.party;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Represents an active player party as an immutable snapshot.
 *
 * <p>A party always has at least one member (the leader). When membership
 * drops to one player the party is typically disbanded by {@link PartyService}.
 */
public record Party(Username leaderId, List<Username> memberIds) {

    /**
     * Constructs a party with the given leader and member list.
     *
     * @param leaderId  the username of the party leader
     * @param memberIds all current member usernames (must include the leader)
     */
    public Party {
        Objects.requireNonNull(leaderId, "leaderId is required");
        Objects.requireNonNull(memberIds, "memberIds is required");
        memberIds = List.copyOf(memberIds);
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
