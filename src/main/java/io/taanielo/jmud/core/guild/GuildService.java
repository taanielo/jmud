package io.taanielo.jmud.core.guild;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Application service owning all persistent guild state.
 *
 * <p>This service is the authoritative source of truth for guild membership: it caches every guild
 * in memory (eagerly loaded from the {@link GuildRepository} at construction) and maintains indices
 * from guild name and member username to guild id for fast lookups. Mutations run on the tick thread
 * via the player command queue (AGENTS.md §5); the service is nonetheless {@code synchronized} so its
 * indices stay internally consistent even if consulted from another thread.
 *
 * <p>Guild creation may cost gold ({@link #CREATION_COST_GOLD}); the gold sink itself is applied by
 * the caller against the player's balance (this service never mutates {@code Player}). Pending
 * invitations are held in memory only and are cleared on accept, decline, or reissue.
 */
@Slf4j
public class GuildService {

    /** Gold charged to found a guild, mirroring existing gold sinks (bank, repair, identify). */
    public static final int CREATION_COST_GOLD = 100;

    /** Minimum guild name length, in characters. */
    public static final int MIN_NAME_LENGTH = 3;

    /** Maximum guild name length, in characters. */
    public static final int MAX_NAME_LENGTH = 24;

    /** Guild names are letters, digits, spaces, apostrophes and hyphens only. */
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9 '-]+");

    private final GuildRepository repository;

    /** guildId → guild snapshot (authoritative). */
    private final Map<GuildId, Guild> guildsById = new ConcurrentHashMap<>();
    /** lowercased guild name → guildId (uniqueness index). */
    private final Map<String, GuildId> guildIdByName = new ConcurrentHashMap<>();
    /** member username → guildId. */
    private final Map<Username, GuildId> guildIdByMember = new ConcurrentHashMap<>();
    /** invitee username → guildId they were invited to (pending invitations). */
    private final Map<Username, GuildId> pendingInvites = new ConcurrentHashMap<>();

    /**
     * Creates a guild service, eagerly loading and indexing every persisted guild.
     *
     * @param repository the persistence port used to load guilds at startup and persist changes
     * @throws GuildRepositoryException when the guild data cannot be read
     */
    public GuildService(GuildRepository repository) throws GuildRepositoryException {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        for (Guild guild : repository.loadAll()) {
            index(guild);
        }
        log.info("Loaded {} guild(s)", guildsById.size());
    }

    // ── Lifecycle operations ──────────────────────────────────────────

    /**
     * Founds a new guild with {@code leader} as its sole member and leader.
     *
     * <p>The name must be unique (case-insensitive), within the allowed length, and made up of
     * permitted characters. The caller is responsible for confirming and charging
     * {@link #CREATION_COST_GOLD} before treating the founding as final.
     *
     * @param leader the founding player
     * @param name   the desired guild name
     * @return the result; on success carries the newly created guild
     */
    public synchronized GuildResult create(Username leader, String name) {
        Objects.requireNonNull(leader, "leader is required");
        if (guildIdByMember.containsKey(leader)) {
            return GuildResult.failure("You are already in a guild. Use GUILD LEAVE first.");
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < MIN_NAME_LENGTH || trimmed.length() > MAX_NAME_LENGTH) {
            return GuildResult.failure(
                "Guild names must be between " + MIN_NAME_LENGTH + " and " + MAX_NAME_LENGTH
                    + " characters.");
        }
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            return GuildResult.failure(
                "Guild names may only contain letters, digits, spaces, apostrophes and hyphens.");
        }
        if (guildIdByName.containsKey(trimmed.toLowerCase(Locale.ROOT))) {
            return GuildResult.failure("A guild named '" + trimmed + "' already exists.");
        }
        Guild guild = Guild.found(GuildId.newId(), trimmed, leader);
        index(guild);
        repository.save(guild);
        return GuildResult.success("You found the guild '" + trimmed + "'. You are its leader.", guild);
    }

    /**
     * Records a pending invitation from {@code inviter} to {@code invitee}. The inviter must be a
     * guild leader; the invitee must be online and not already in a guild.
     *
     * @param inviter       the inviting player (must be a guild leader)
     * @param invitee       the player being invited
     * @param inviteeOnline whether the invitee is currently connected
     * @return the result; on success carries the inviter's guild
     */
    public synchronized GuildResult invite(Username inviter, Username invitee, boolean inviteeOnline) {
        Objects.requireNonNull(inviter, "inviter is required");
        Objects.requireNonNull(invitee, "invitee is required");
        if (invitee.equals(inviter)) {
            return GuildResult.failure("You cannot invite yourself.");
        }
        @Nullable Guild guild = guildOf(inviter).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!guild.isLeader(inviter)) {
            return GuildResult.failure("Only the guild leader can invite players.");
        }
        if (!inviteeOnline) {
            return GuildResult.failure(invitee.getValue() + " is not online.");
        }
        if (guildIdByMember.containsKey(invitee)) {
            return GuildResult.failure(invitee.getValue() + " is already in a guild.");
        }
        pendingInvites.put(invitee, guild.id());
        return GuildResult.success("You invite " + invitee.getValue() + " to " + guild.name() + ".", guild);
    }

    /**
     * Accepts the pending invitation for {@code invitee}, adding them to the guild's roster.
     *
     * @param invitee the player accepting an invitation
     * @return the result; on success carries the joined guild
     */
    public synchronized GuildResult accept(Username invitee) {
        Objects.requireNonNull(invitee, "invitee is required");
        @Nullable GuildId guildId = pendingInvites.remove(invitee);
        if (guildId == null) {
            return GuildResult.failure("You have no pending guild invitation.");
        }
        if (guildIdByMember.containsKey(invitee)) {
            return GuildResult.failure("You are already in a guild.");
        }
        @Nullable Guild guild = guildsById.get(guildId);
        if (guild == null) {
            return GuildResult.failure("The guild you were invited to no longer exists.");
        }
        Guild updated = guild.withMember(invitee);
        index(updated);
        repository.save(updated);
        return GuildResult.success("You have joined " + updated.name() + ".", updated);
    }

    /**
     * Declines the pending invitation for {@code invitee}.
     *
     * @param invitee the player declining an invitation
     * @return the result
     */
    public synchronized GuildResult decline(Username invitee) {
        Objects.requireNonNull(invitee, "invitee is required");
        @Nullable GuildId guildId = pendingInvites.remove(invitee);
        if (guildId == null) {
            return GuildResult.failure("You have no pending guild invitation.");
        }
        @Nullable Guild guild = guildsById.get(guildId);
        String name = guild == null ? "the guild" : guild.name();
        return new GuildResult(true, "You decline the invitation to " + name + ".", guild);
    }

    /**
     * Removes {@code member} from their guild. If the leader leaves and other members remain,
     * leadership transfers to the longest-tenured member; if the last member leaves, the guild is
     * disbanded.
     *
     * @param member the player leaving
     * @return the result; on success the guild snapshot reflects the post-departure roster, or is
     *         empty-membership when the guild was disbanded by the departure
     */
    public synchronized GuildResult leave(Username member) {
        Objects.requireNonNull(member, "member is required");
        @Nullable Guild guild = guildOf(member).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        Guild updated = guild.withoutMember(member);
        guildIdByMember.remove(member);
        if (updated.memberCount() == 0) {
            removeGuild(guild);
            repository.delete(guild.id());
            return GuildResult.success(
                "You leave " + guild.name() + ". With no members left, it is disbanded.", updated);
        }
        guildsById.put(updated.id(), updated);
        String suffix = guild.isLeader(member)
            ? " " + updated.leaderId().getValue() + " is now the leader."
            : "";
        return GuildResult.success("You leave " + guild.name() + "." + suffix, updated);
    }

    /**
     * Removes {@code target} from the guild led by {@code leader}.
     *
     * @param leader the guild leader issuing the kick
     * @param target the member to remove
     * @return the result; on success carries the updated guild
     */
    public synchronized GuildResult kick(Username leader, Username target) {
        Objects.requireNonNull(leader, "leader is required");
        Objects.requireNonNull(target, "target is required");
        @Nullable Guild guild = guildOf(leader).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!guild.isLeader(leader)) {
            return GuildResult.failure("Only the guild leader can kick members.");
        }
        if (target.equals(leader)) {
            return GuildResult.failure("You cannot kick yourself. Use GUILD DISBAND or GUILD LEAVE.");
        }
        if (!guild.isMember(target)) {
            return GuildResult.failure(target.getValue() + " is not in your guild.");
        }
        Guild updated = guild.withoutMember(target);
        guildIdByMember.remove(target);
        guildsById.put(updated.id(), updated);
        return GuildResult.success("You remove " + target.getValue() + " from " + guild.name() + ".", updated);
    }

    /**
     * Disbands the guild led by {@code leader}, clearing it from every member's record.
     *
     * @param leader the guild leader
     * @return the result; on success carries the (now removed) guild snapshot so the caller can
     *         notify and clear every former member
     */
    public synchronized GuildResult disband(Username leader) {
        Objects.requireNonNull(leader, "leader is required");
        @Nullable Guild guild = guildOf(leader).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!guild.isLeader(leader)) {
            return GuildResult.failure("Only the guild leader can disband the guild.");
        }
        removeGuild(guild);
        repository.delete(guild.id());
        return GuildResult.success(guild.name() + " has been disbanded.", guild);
    }

    // ── Queries ───────────────────────────────────────────────────────

    /**
     * Returns the guild the given player belongs to, if any.
     *
     * @param username the player to look up
     * @return the player's guild, or empty
     */
    public Optional<Guild> guildOf(Username username) {
        Objects.requireNonNull(username, "username is required");
        @Nullable GuildId guildId = guildIdByMember.get(username);
        if (guildId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(guildsById.get(guildId));
    }

    /**
     * Returns the display name (tag) of the guild the given player belongs to, if any.
     *
     * @param username the player to look up
     * @return the guild name, or empty when the player is guildless
     */
    public Optional<String> guildTag(Username username) {
        return guildOf(username).map(Guild::name);
    }

    /**
     * Returns the guild with the given name (case-insensitive), if any.
     *
     * @param name the guild name to look up
     * @return the matching guild, or empty
     */
    public Optional<Guild> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        @Nullable GuildId guildId = guildIdByName.get(name.trim().toLowerCase(Locale.ROOT));
        return guildId == null ? Optional.empty() : Optional.ofNullable(guildsById.get(guildId));
    }

    /**
     * Returns the guild id currently pending acceptance by the given invitee, if any.
     *
     * @param invitee the invited player
     * @return the pending guild id, or empty
     */
    public Optional<GuildId> getPendingInviteGuild(Username invitee) {
        Objects.requireNonNull(invitee, "invitee is required");
        return Optional.ofNullable(pendingInvites.get(invitee));
    }

    // ── Indexing helpers ──────────────────────────────────────────────

    /** Adds or replaces the given guild in every in-memory index. */
    private void index(Guild guild) {
        @Nullable Guild previous = guildsById.put(guild.id(), guild);
        if (previous != null) {
            guildIdByName.remove(previous.name().toLowerCase(Locale.ROOT));
            for (GuildMember m : previous.members()) {
                guildIdByMember.remove(m.username());
            }
        }
        guildIdByName.put(guild.name().toLowerCase(Locale.ROOT), guild.id());
        for (GuildMember m : guild.members()) {
            guildIdByMember.put(m.username(), guild.id());
        }
    }

    /** Removes the given guild from every in-memory index. */
    private void removeGuild(Guild guild) {
        guildsById.remove(guild.id());
        guildIdByName.remove(guild.name().toLowerCase(Locale.ROOT));
        for (GuildMember m : guild.members()) {
            guildIdByMember.remove(m.username());
        }
        pendingInvites.values().removeIf(id -> id.equals(guild.id()));
    }
}
