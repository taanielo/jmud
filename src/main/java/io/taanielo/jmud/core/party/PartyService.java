package io.taanielo.jmud.core.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * In-memory party management service.
 *
 * <p>Manages party formation, membership, invitations, and disbanding.
 * All mutating operations are {@code synchronized} to guarantee consistent
 * state across concurrent virtual-thread player sessions.
 *
 * <p>No persistence is used; parties vanish on server restart.
 */
public class PartyService {

    /**
     * Result of a party operation, carrying a success flag and a player-facing message.
     */
    public record PartyResult(boolean success, String message) {}

    /** partyId → Party snapshot. */
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    /** member username → partyId. */
    private final Map<Username, UUID> memberToParty = new ConcurrentHashMap<>();
    /** invitee username → inviter username (pending invitations). */
    private final Map<Username, Username> pendingInvites = new ConcurrentHashMap<>();
    /** follower username → leader username (auto-follow relationships). */
    private final Map<Username, Username> followerToLeader = new ConcurrentHashMap<>();

    // ── Party lifecycle ───────────────────────────────────────────────

    /**
     * Creates a new party with {@code leader} as its sole member.
     *
     * @param leader the player forming the party
     * @return result describing success or failure
     */
    public synchronized PartyResult form(Username leader) {
        Objects.requireNonNull(leader, "leader is required");
        if (memberToParty.containsKey(leader)) {
            return new PartyResult(false, "You are already in a party. Use PARTY LEAVE first.");
        }
        UUID partyId = UUID.randomUUID();
        Party party = new Party(leader, List.of(leader));
        parties.put(partyId, party);
        memberToParty.put(leader, partyId);
        return new PartyResult(true, "You have formed a new party.");
    }

    /**
     * Records a pending invitation from {@code inviter} to {@code invitee}.
     *
     * <p>The inviter must be the party leader. The invitee must be online and
     * not already in a party.
     *
     * @param inviter        the player sending the invitation (must be party leader)
     * @param invitee        the player to invite
     * @param inviteeOnline  whether the invitee is currently connected
     * @return result describing success or failure
     */
    public synchronized PartyResult invite(Username inviter, Username invitee, boolean inviteeOnline) {
        Objects.requireNonNull(inviter, "inviter is required");
        Objects.requireNonNull(invitee, "invitee is required");
        if (invitee.equals(inviter)) {
            return new PartyResult(false, "You cannot invite yourself.");
        }
        if (!inviteeOnline) {
            return new PartyResult(false, invitee.getValue() + " is not online.");
        }
        UUID partyId = memberToParty.get(inviter);
        if (partyId == null) {
            return new PartyResult(false, "You are not in a party. Use PARTY FORM first.");
        }
        Party party = parties.get(partyId);
        if (party == null || !party.isLeader(inviter)) {
            return new PartyResult(false, "Only the party leader can invite players.");
        }
        if (memberToParty.containsKey(invitee)) {
            return new PartyResult(false, invitee.getValue() + " is already in a party.");
        }
        pendingInvites.put(invitee, inviter);
        return new PartyResult(true, "Party invitation sent to " + invitee.getValue() + ".");
    }

    /**
     * Accepts the pending invitation for {@code invitee}, adding them to the party.
     *
     * @param invitee the player accepting an invitation
     * @return result describing success or failure
     */
    public synchronized PartyResult accept(Username invitee) {
        Objects.requireNonNull(invitee, "invitee is required");
        Username inviter = pendingInvites.remove(invitee);
        if (inviter == null) {
            return new PartyResult(false, "You have no pending party invitation.");
        }
        if (memberToParty.containsKey(invitee)) {
            return new PartyResult(false, "You are already in a party.");
        }
        UUID partyId = memberToParty.get(inviter);
        if (partyId == null) {
            return new PartyResult(false, "The party you were invited to no longer exists.");
        }
        Party old = parties.get(partyId);
        if (old == null) {
            return new PartyResult(false, "The party you were invited to no longer exists.");
        }
        List<Username> newMembers = new ArrayList<>(old.memberIds());
        newMembers.add(invitee);
        parties.put(partyId, old.withMembers(old.leaderId(), newMembers));
        memberToParty.put(invitee, partyId);
        return new PartyResult(true, "You have joined " + inviter.getValue() + "'s party.");
    }

    /**
     * Declines the pending invitation for {@code invitee}.
     *
     * @param invitee the player declining an invitation
     * @return result describing success or failure
     */
    public synchronized PartyResult decline(Username invitee) {
        Objects.requireNonNull(invitee, "invitee is required");
        Username inviter = pendingInvites.remove(invitee);
        if (inviter == null) {
            return new PartyResult(false, "You have no pending party invitation.");
        }
        return new PartyResult(true, "You declined the party invitation from " + inviter.getValue() + ".");
    }

    /**
     * Removes {@code member} from their current party.
     *
     * <p>If only one member remains after the leave, the party is automatically disbanded.
     * If the leaving member was the leader, leadership is transferred to the next member.
     *
     * @param member the player leaving the party
     * @return result describing success or failure
     */
    public synchronized PartyResult leave(Username member) {
        Objects.requireNonNull(member, "member is required");
        UUID partyId = memberToParty.remove(member);
        if (partyId == null) {
            return new PartyResult(false, "You are not in a party.");
        }
        clearFollowsInvolving(member);
        Party old = parties.get(partyId);
        if (old == null) {
            return new PartyResult(true, "You have left the party.");
        }
        List<Username> remaining = old.memberIds().stream()
            .filter(m -> !m.equals(member))
            .toList();
        if (remaining.size() <= 1) {
            // Disband: remove party and remaining member mapping
            parties.remove(partyId);
            for (Username m : remaining) {
                memberToParty.remove(m);
            }
            return new PartyResult(true, "You have left the party. The party has been disbanded.");
        }
        Username newLeader = old.isLeader(member) ? remaining.get(0) : old.leaderId();
        parties.put(partyId, old.withMembers(newLeader, remaining));
        String suffix = old.isLeader(member)
            ? " " + newLeader.getValue() + " is now the party leader."
            : "";
        return new PartyResult(true, "You have left the party." + suffix);
    }

    /**
     * Disbands the party entirely. Only the party leader may invoke this.
     *
     * @param leader the player disbanding the party
     * @return result describing success or failure
     */
    public synchronized PartyResult disband(Username leader) {
        Objects.requireNonNull(leader, "leader is required");
        UUID partyId = memberToParty.get(leader);
        if (partyId == null) {
            return new PartyResult(false, "You are not in a party.");
        }
        Party party = parties.get(partyId);
        if (party == null || !party.isLeader(leader)) {
            return new PartyResult(false, "Only the party leader can disband the party.");
        }
        parties.remove(partyId);
        for (Username m : party.memberIds()) {
            memberToParty.remove(m);
            clearFollowsInvolving(m);
        }
        return new PartyResult(true, "Party disbanded.");
    }

    /**
     * Removes {@code target} from the party at the request of the party leader.
     *
     * <p>Only the party leader may kick, and only members of the leader's own party may be
     * kicked. A leader cannot kick themselves (they should use {@link #disband(Username)} or
     * {@link #leave(Username)} instead). Any auto-follow relationship involving the kicked
     * member is cleared. If only the leader remains after the kick, the party is automatically
     * disbanded, mirroring {@link #leave(Username)}'s "last member" behaviour.
     *
     * @param leader the player requesting the kick (must be the party leader)
     * @param target the member to remove
     * @return result describing success or failure
     */
    public synchronized PartyResult kick(Username leader, Username target) {
        Objects.requireNonNull(leader, "leader is required");
        Objects.requireNonNull(target, "target is required");
        UUID partyId = memberToParty.get(leader);
        if (partyId == null) {
            return new PartyResult(false, "You are not in a party.");
        }
        Party party = parties.get(partyId);
        if (party == null || !party.isLeader(leader)) {
            return new PartyResult(false, "Only the party leader can kick members.");
        }
        if (target.equals(leader)) {
            return new PartyResult(false,
                "You cannot kick yourself. Use PARTY LEAVE or PARTY DISBAND instead.");
        }
        UUID targetParty = memberToParty.get(target);
        if (targetParty == null || !targetParty.equals(partyId)) {
            return new PartyResult(false, target.getValue() + " is not a member of your party.");
        }
        memberToParty.remove(target);
        clearFollowsInvolving(target);
        List<Username> remaining = party.memberIds().stream()
            .filter(m -> !m.equals(target))
            .toList();
        if (remaining.size() <= 1) {
            // Disband: remove party and remaining member mapping
            parties.remove(partyId);
            for (Username m : remaining) {
                memberToParty.remove(m);
            }
            return new PartyResult(true,
                "You remove " + target.getValue()
                    + " from the party. The party has been disbanded.");
        }
        parties.put(partyId, party.withMembers(party.leaderId(), remaining));
        return new PartyResult(true, "You remove " + target.getValue() + " from the party.");
    }

    // ── Loot mode ─────────────────────────────────────────────────────

    /**
     * Sets the party's {@link LootMode}. Only the party leader may change it.
     *
     * <p>Switching mode resets the round-robin rotation pointer so a freshly enabled round-robin
     * begins with the first eligible member.
     *
     * @param leader the player changing the loot mode (must be the party leader)
     * @param mode   the loot mode to apply
     * @return result describing success or failure
     */
    public synchronized PartyResult setLootMode(Username leader, LootMode mode) {
        Objects.requireNonNull(leader, "leader is required");
        Objects.requireNonNull(mode, "mode is required");
        UUID partyId = memberToParty.get(leader);
        if (partyId == null) {
            return new PartyResult(false, "You are not in a party. Use PARTY FORM first.");
        }
        Party party = parties.get(partyId);
        if (party == null || !party.isLeader(leader)) {
            return new PartyResult(false, "Only the party leader can change the loot mode.");
        }
        parties.put(partyId, party.withLootMode(mode));
        return new PartyResult(true, "Party loot mode set to " + mode.label() + ".");
    }

    /**
     * Returns the {@link LootMode} of {@code member}'s party, or {@link LootMode#FREE} when the
     * player is not in a party.
     *
     * @param member the reference player
     * @return the party's loot mode, defaulting to {@link LootMode#FREE}
     */
    public LootMode lootMode(Username member) {
        Objects.requireNonNull(member, "member is required");
        return findParty(member).map(Party::lootMode).orElse(LootMode.FREE);
    }

    /**
     * Selects the next party member to receive a round-robin loot item, advancing the party's
     * rotation pointer past the chosen recipient so a subsequent call continues with the following
     * member. Candidates are tried starting at the current pointer; the first for which
     * {@code canReceive} returns {@code true} is chosen. When no candidate can receive the item the
     * pointer is left unchanged and an empty result is returned, letting the caller fall back to a
     * floor drop.
     *
     * @param member     any member of the party whose rotation to advance
     * @param eligible   the ordered list of members eligible to receive (typically those present in
     *                   the kill room); must be stable across calls for the rotation to be meaningful
     * @param canReceive predicate deciding whether a given candidate can currently hold the item
     * @return the chosen recipient, or empty when nobody eligible can receive the item
     */
    public synchronized Optional<Username> nextLootRecipient(
        Username member,
        List<Username> eligible,
        Predicate<Username> canReceive
    ) {
        Objects.requireNonNull(member, "member is required");
        Objects.requireNonNull(eligible, "eligible is required");
        Objects.requireNonNull(canReceive, "canReceive is required");
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        UUID partyId = memberToParty.get(member);
        if (partyId == null) {
            return Optional.empty();
        }
        Party party = parties.get(partyId);
        if (party == null) {
            return Optional.empty();
        }
        int size = eligible.size();
        int start = Math.floorMod(party.lootCursor(), size);
        for (int i = 0; i < size; i++) {
            int index = (start + i) % size;
            Username candidate = eligible.get(index);
            if (canReceive.test(candidate)) {
                parties.put(partyId, party.withLootCursor(index + 1));
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // ── Auto-follow ────────────────────────────────────────────────────

    /**
     * Starts {@code follower} auto-following {@code leader}, so the follower is moved along whenever
     * the leader successfully walks to an adjacent room (see the socket movement path).
     *
     * <p>Both players must currently belong to the same party and the leader must be online; a player
     * may follow at most one leader at a time (a new call replaces any prior relationship).
     *
     * @param follower     the player who wants to auto-follow
     * @param leader       the player to follow
     * @param leaderOnline whether the leader is currently connected
     * @return result describing success or failure
     */
    public synchronized PartyResult follow(Username follower, Username leader, boolean leaderOnline) {
        Objects.requireNonNull(follower, "follower is required");
        Objects.requireNonNull(leader, "leader is required");
        if (follower.equals(leader)) {
            return new PartyResult(false, "You cannot follow yourself.");
        }
        if (!leaderOnline) {
            return new PartyResult(false, leader.getValue() + " is not online.");
        }
        UUID followerParty = memberToParty.get(follower);
        UUID leaderParty = memberToParty.get(leader);
        if (followerParty == null || leaderParty == null || !followerParty.equals(leaderParty)) {
            return new PartyResult(false, leader.getValue() + " is not in your party.");
        }
        followerToLeader.put(follower, leader);
        return new PartyResult(true, "You start following " + leader.getValue() + ".");
    }

    /**
     * Stops {@code follower} auto-following whoever they were following.
     *
     * @param follower the player who wants to stop following
     * @return result describing success or failure
     */
    public synchronized PartyResult unfollow(Username follower) {
        Objects.requireNonNull(follower, "follower is required");
        Username leader = followerToLeader.remove(follower);
        if (leader == null) {
            return new PartyResult(false, "You are not following anyone.");
        }
        return new PartyResult(true, "You stop following " + leader.getValue() + ".");
    }

    /**
     * Returns the leader that {@code follower} is currently auto-following, if any.
     *
     * @param follower the player to look up
     * @return the followed leader, or empty
     */
    public Optional<Username> leaderOf(Username follower) {
        Objects.requireNonNull(follower, "follower is required");
        return Optional.ofNullable(followerToLeader.get(follower));
    }

    /**
     * Returns the usernames of every player currently auto-following {@code leader}.
     *
     * @param leader the followed player
     * @return the followers, in no particular order (possibly empty)
     */
    public List<Username> followersOf(Username leader) {
        Objects.requireNonNull(leader, "leader is required");
        return followerToLeader.entrySet().stream()
            .filter(e -> e.getValue().equals(leader))
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Returns {@code true} when both players are currently members of the same party.
     *
     * @param first  the first player
     * @param second the second player
     * @return whether both share a party
     */
    public boolean inSameParty(Username first, Username second) {
        Objects.requireNonNull(first, "first is required");
        Objects.requireNonNull(second, "second is required");
        UUID firstParty = memberToParty.get(first);
        UUID secondParty = memberToParty.get(second);
        return firstParty != null && firstParty.equals(secondParty);
    }

    /**
     * Removes any auto-follow relationship in which {@code username} participates, whether as the
     * follower or as the followed leader. Called when a player leaves/disbands their party or
     * disconnects so no dangling relationship can leave a follower stuck.
     *
     * @param username the player whose follow relationships to clear
     */
    public synchronized void clearFollowsInvolving(Username username) {
        Objects.requireNonNull(username, "username is required");
        followerToLeader.remove(username);
        followerToLeader.entrySet().removeIf(e -> e.getValue().equals(username));
    }

    // ── Queries ───────────────────────────────────────────────────────

    /**
     * Returns the party that {@code username} belongs to, if any.
     *
     * @param username the player to look up
     * @return an {@code Optional} containing the party, or empty
     */
    public Optional<Party> findParty(Username username) {
        Objects.requireNonNull(username, "username is required");
        UUID partyId = memberToParty.get(username);
        if (partyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(parties.get(partyId));
    }

    /**
     * Returns the party members of {@code username}'s party who are currently
     * in {@code roomId}. Includes the player themselves.
     *
     * <p>If the player is not in a party, the returned list contains only
     * {@code username} (so callers can always iterate recipients).
     *
     * @param username   the reference player (killer/actor)
     * @param roomId     the room to filter by
     * @param locationFn function that resolves a username to their current room
     * @return usernames of party members in the room
     */
    public List<Username> getPartyMembersInRoom(
        Username username,
        RoomId roomId,
        Function<Username, Optional<RoomId>> locationFn
    ) {
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(locationFn, "locationFn is required");
        Optional<Party> partyOpt = findParty(username);
        if (partyOpt.isEmpty()) {
            return List.of(username);
        }
        return partyOpt.get().memberIds().stream()
            .filter(m -> locationFn.apply(m)
                .map(r -> r.equals(roomId))
                .orElse(false))
            .toList();
    }

    /**
     * Returns the usernames of all party members of {@code username} except
     * {@code username} themselves. Useful for broadcasting party notifications.
     *
     * @param username the reference player
     * @return other members in the same party, or an empty list
     */
    public List<Username> getOtherMembers(Username username) {
        Objects.requireNonNull(username, "username is required");
        UUID partyId = memberToParty.get(username);
        if (partyId == null) {
            return List.of();
        }
        Party party = parties.get(partyId);
        if (party == null) {
            return List.of();
        }
        return party.memberIds().stream()
            .filter(m -> !m.equals(username))
            .toList();
    }

    /**
     * Returns the pending inviter for the given invitee, if an invitation exists.
     *
     * @param invitee the player who received an invite
     * @return the inviter username, or empty
     */
    public Optional<Username> getPendingInviter(Username invitee) {
        Objects.requireNonNull(invitee, "invitee is required");
        return Optional.ofNullable(pendingInvites.get(invitee));
    }
}
