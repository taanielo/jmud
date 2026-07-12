package io.taanielo.jmud.core.guild;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.Item;

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

    /**
     * Maximum number of items a guild's shared vault may hold. Larger than a personal bank vault since
     * it is pooled across the whole roster.
     */
    public static final int VAULT_CAPACITY = 40;

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
     * Records a pending invitation from {@code inviter} to {@code invitee}. The inviter must be the
     * guild leader or an officer; the invitee must be online and not already in a guild.
     *
     * @param inviter       the inviting player (must be the guild leader or an officer)
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
        if (!guild.canModerate(inviter)) {
            return GuildResult.failure("Only the guild leader or an officer can invite players.");
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
     * Removes {@code target} from the guild moderated by {@code remover}. The remover must be the
     * guild leader or an officer; the guild leader can never be kicked.
     *
     * @param remover the guild leader or officer issuing the kick
     * @param target  the member to remove
     * @return the result; on success carries the updated guild
     */
    public synchronized GuildResult kick(Username remover, Username target) {
        Objects.requireNonNull(remover, "remover is required");
        Objects.requireNonNull(target, "target is required");
        @Nullable Guild guild = guildOf(remover).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!guild.canModerate(remover)) {
            return GuildResult.failure("Only the guild leader or an officer can kick members.");
        }
        if (target.equals(remover)) {
            return GuildResult.failure("You cannot kick yourself. Use GUILD DISBAND or GUILD LEAVE.");
        }
        if (!guild.isMember(target)) {
            return GuildResult.failure(target.getValue() + " is not in your guild.");
        }
        if (guild.isLeader(target)) {
            return GuildResult.failure("You cannot kick the guild leader.");
        }
        Guild updated = guild.withoutMember(target);
        guildIdByMember.remove(target);
        guildsById.put(updated.id(), updated);
        return GuildResult.success("You remove " + target.getValue() + " from " + guild.name() + ".", updated);
    }

    /**
     * Promotes {@code target} to {@link GuildRank#OFFICER} within the guild led by {@code leader}.
     * Leader-only: the target must already be a member of the leader's guild and cannot be the
     * leader themself. Promoting a member who is already an officer is reported as a no-op.
     *
     * @param leader the guild leader issuing the promotion
     * @param target the member to promote
     * @return the result; on success carries the updated guild
     */
    public synchronized GuildResult promote(Username leader, Username target) {
        Objects.requireNonNull(leader, "leader is required");
        Objects.requireNonNull(target, "target is required");
        @Nullable Guild guild = guildOf(leader).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!guild.isLeader(leader)) {
            return GuildResult.failure("Only the guild leader can promote members.");
        }
        if (target.equals(leader)) {
            return GuildResult.failure("You cannot promote yourself.");
        }
        if (!guild.isMember(target)) {
            return GuildResult.failure(target.getValue() + " is not in your guild.");
        }
        if (guild.isOfficer(target)) {
            return GuildResult.failure(target.getValue() + " is already an officer.");
        }
        Guild updated = guild.withMemberRank(target, GuildRank.OFFICER);
        guildsById.put(updated.id(), updated);
        repository.save(updated);
        return GuildResult.success(
            "You promote " + target.getValue() + " to officer in " + guild.name() + ".", updated);
    }

    /**
     * Demotes {@code target} from {@link GuildRank#OFFICER} back to {@link GuildRank#MEMBER} within
     * the guild led by {@code leader}. Leader-only: the target must already be a member of the
     * leader's guild and cannot be the leader themself. Demoting someone who is not currently an
     * officer is reported as a no-op.
     *
     * @param leader the guild leader issuing the demotion
     * @param target the officer to demote
     * @return the result; on success carries the updated guild
     */
    public synchronized GuildResult demote(Username leader, Username target) {
        Objects.requireNonNull(leader, "leader is required");
        Objects.requireNonNull(target, "target is required");
        @Nullable Guild guild = guildOf(leader).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!guild.isLeader(leader)) {
            return GuildResult.failure("Only the guild leader can demote officers.");
        }
        if (target.equals(leader)) {
            return GuildResult.failure("You cannot demote yourself.");
        }
        if (!guild.isMember(target)) {
            return GuildResult.failure(target.getValue() + " is not in your guild.");
        }
        if (!guild.isOfficer(target)) {
            return GuildResult.failure(target.getValue() + " is not an officer.");
        }
        Guild updated = guild.withMemberRank(target, GuildRank.MEMBER);
        guildsById.put(updated.id(), updated);
        repository.save(updated);
        return GuildResult.success(
            "You demote " + target.getValue() + " to member in " + guild.name() + ".", updated);
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

    // ── Treasury operations ───────────────────────────────────────────

    /**
     * Deposits {@code amount} gold from a member into their guild's shared treasury. Validates that
     * the caller is in a guild and that the amount is positive, then persists the updated guild.
     *
     * <p>This service only mutates the treasury; the caller is responsible for debiting the same
     * amount from the acting player's personal balance in the same command so the two never drift.
     *
     * @param member the depositing player
     * @param amount the amount of gold to move into the treasury (must be positive)
     * @return the result; on success carries the updated guild snapshot
     */
    public synchronized GuildResult deposit(Username member, int amount) {
        Objects.requireNonNull(member, "member is required");
        @Nullable Guild guild = guildOf(member).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (amount <= 0) {
            return GuildResult.failure("Deposit a positive amount of gold.");
        }
        Guild updated = guild.depositTreasury(amount);
        guildsById.put(updated.id(), updated);
        repository.save(updated);
        return GuildResult.success(
            "You deposit " + amount + " gold into the " + guild.name() + " treasury.", updated);
    }

    /**
     * Withdraws {@code amount} gold from the guild treasury into the leader's hands. Only the guild
     * leader may withdraw; the amount must be positive and no greater than the current balance.
     *
     * <p>This service only mutates the treasury; the caller is responsible for crediting the same
     * amount to the acting player's personal balance in the same command so the two never drift.
     *
     * @param member the player attempting to withdraw (must be the guild leader)
     * @param amount the amount of gold to move out of the treasury (must be positive)
     * @return the result; on success carries the updated guild snapshot
     */
    public synchronized GuildResult withdraw(Username member, int amount) {
        Objects.requireNonNull(member, "member is required");
        @Nullable Guild guild = guildOf(member).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!guild.isLeader(member)) {
            return GuildResult.failure("Only the guild leader can withdraw from the treasury.");
        }
        if (amount <= 0) {
            return GuildResult.failure("Withdraw a positive amount of gold.");
        }
        if (amount > guild.treasuryGold()) {
            return GuildResult.failure(
                "The " + guild.name() + " treasury only holds " + guild.treasuryGold() + " gold.");
        }
        Guild updated = guild.withdrawTreasury(amount);
        guildsById.put(updated.id(), updated);
        repository.save(updated);
        return GuildResult.success(
            "You withdraw " + amount + " gold from the " + guild.name() + " treasury.", updated);
    }

    // ── Vault operations ──────────────────────────────────────────────

    /**
     * Stores {@code item} from a member into their guild's shared item vault. Validates that the caller
     * is in a guild and that the vault is not already at {@link #VAULT_CAPACITY} capacity, then persists
     * the updated guild.
     *
     * <p>This service only mutates the vault; the caller is responsible for removing the same item from
     * the acting player's inventory (unequipping it first if worn) in the same command so the item is
     * moved rather than duplicated.
     *
     * @param member the storing player (any member may deposit)
     * @param item   the item to move into the vault
     * @return the result; on success carries the updated guild snapshot
     */
    public synchronized GuildResult storeItem(Username member, Item item) {
        Objects.requireNonNull(member, "member is required");
        Objects.requireNonNull(item, "item is required");
        @Nullable Guild guild = guildOf(member).orElse(null);
        if (guild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (guild.vaultedItems().size() >= VAULT_CAPACITY) {
            return GuildResult.failure("The " + guild.name() + " vault is full.");
        }
        Guild updated = guild.withVaultedItem(new VaultedItem(item, member));
        guildsById.put(updated.id(), updated);
        repository.save(updated);
        return GuildResult.success(
            "You store " + item.getName() + " in the " + guild.name() + " vault.", updated);
    }

    /**
     * Claims an item matching {@code itemName} from the guild's shared item vault into the caller's
     * hands. Restricted to the guild leader or an officer (the same trust tier as kick/promote), so a
     * junior member cannot drain shared gear. The caller must be able to carry the item.
     *
     * <p>This service only mutates the vault; the caller is responsible for adding the returned item to
     * the acting player's inventory in the same command so the item is moved rather than duplicated.
     *
     * @param member               the claiming player (must be leader or officer)
     * @param itemName             the name or id of the item to claim
     * @param currentCarriedWeight the total weight the player is already carrying
     * @param maxCarry             the player's maximum carry weight
     * @return the result; on success carries the updated guild snapshot and the claimed item
     */
    public synchronized GuildVaultResult claimItem(
        Username member, String itemName, int currentCarriedWeight, int maxCarry) {
        Objects.requireNonNull(member, "member is required");
        @Nullable Guild guild = guildOf(member).orElse(null);
        if (guild == null) {
            return GuildVaultResult.failure("You are not in a guild.");
        }
        if (!guild.canModerate(member)) {
            return GuildVaultResult.failure(
                "Only the guild leader or an officer can claim items from the vault.");
        }
        String normalized = itemName == null ? "" : itemName.trim();
        if (normalized.isEmpty()) {
            return GuildVaultResult.failure("Claim what? Usage: GUILD CLAIM <item name>");
        }
        @Nullable VaultedItem match = matchVaulted(guild.vaultedItems(), normalized);
        if (match == null) {
            return GuildVaultResult.failure(
                "The " + guild.name() + " vault has no '" + normalized + "'.");
        }
        if (currentCarriedWeight + match.item().getWeight() > maxCarry) {
            return GuildVaultResult.failure(
                "You can't carry " + match.item().getName() + " right now. Lighten your load first.");
        }
        Guild updated = guild.withoutVaultedItem(match);
        guildsById.put(updated.id(), updated);
        repository.save(updated);
        return GuildVaultResult.success(
            "You claim " + match.item().getName() + " from the " + guild.name() + " vault.",
            updated, match.item());
    }

    /**
     * Finds the first vault entry whose item name or id equals or is prefixed by {@code input}
     * (case-insensitive), or {@code null} when none match.
     */
    @Nullable
    private static VaultedItem matchVaulted(List<VaultedItem> items, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (VaultedItem vaulted : items) {
            String name = vaulted.item().getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return vaulted;
            }
            String id = vaulted.item().getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return vaulted;
            }
        }
        return null;
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
