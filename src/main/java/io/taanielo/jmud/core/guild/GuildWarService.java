package io.taanielo.jmud.core.guild;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.tick.Tickable;

/**
 * Domain service governing direct guild-vs-guild competition: declared guild wars (issue #731).
 *
 * <p>A guild war is a running tally of consensual {@code DUEL} wins between the members of two rival
 * guilds. It is started by a propose/accept handshake between the two guild leaders (mirroring the
 * {@code MARRY}/{@code TRADE}/{@code DUEL} UX): the proposing leader issues {@code GUILD WAR <guild>}
 * and the target guild's leader must {@code GUILD WAR ACCEPT} within {@link #PROPOSAL_TIMEOUT_TICKS}
 * ticks (~60s) or the declaration silently expires. Only guild leaders may propose, accept, decline
 * or concede, and a guild may be in only one war at a time (on either side).
 *
 * <p>Only the <em>proposal</em> half is transient and owned here (keyed by the target guild's id, aged
 * out once per tick like {@link io.taanielo.jmud.core.social.MarriageService}); the accepted war itself
 * is persisted on each {@link Guild} aggregate through {@link GuildService}, so it survives a restart.
 * While a war is active, {@link #recordDuelWin} — invoked from the single duel-resolution choke point —
 * awards one war point to the winner's guild whenever a duel resolves between live members of the two
 * warring guilds. The first guild to {@link GuildWar#POINTS_TO_WIN} war points wins automatically, its
 * persistent {@link Guild#warWins() warWins} counter increments, and a server-wide announcement fires.
 *
 * <p>All guild-state mutation runs on the tick thread via {@link GuildService} (AGENTS.md §5); the
 * transient proposal registry is nonetheless {@code synchronized} so it stays consistent when a reader
 * thread inspects it concurrently with the tick thread.
 */
public class GuildWarService implements Tickable {

    /** Acceptance window for a guild-war declaration, in ticks (~60 seconds at 1s/tick). */
    public static final int PROPOSAL_TIMEOUT_TICKS = 60;

    private final GuildService guildService;
    private final MessageBroadcaster messageBroadcaster;

    /** target guild id → the pending war declaration awaiting that guild leader's answer. */
    private final Map<GuildId, PendingWar> proposals = new ConcurrentHashMap<>();

    /** A pending, not-yet-accepted war declaration and its countdown to expiry. */
    private record PendingWar(GuildId proposerGuildId, int ticksRemaining) {
        PendingWar tickDown() {
            return new PendingWar(proposerGuildId, ticksRemaining - 1);
        }

        boolean expired() {
            return ticksRemaining <= 0;
        }
    }

    /**
     * Creates the guild-war service.
     *
     * @param guildService       the authoritative owner of guild state and persistence
     * @param messageBroadcaster the sanctioned fan-out used to announce declarations, scores and wins
     */
    public GuildWarService(GuildService guildService, MessageBroadcaster messageBroadcaster) {
        this.guildService = Objects.requireNonNull(guildService, "guildService is required");
        this.messageBroadcaster =
            Objects.requireNonNull(messageBroadcaster, "messageBroadcaster is required");
    }

    /**
     * Declares war on another guild, sending a challenge that guild's leader must accept.
     *
     * <p>Leader-only on the proposing side: the caller must lead a guild that is not already at war and
     * does not already have a pending declaration outstanding. The target must be a different, existing
     * guild that is not itself already at war. On success a pending declaration is recorded with a
     * {@link #PROPOSAL_TIMEOUT_TICKS}-tick window and the target guild's leader is notified.
     *
     * @param leader          the proposing player (must be their guild's leader)
     * @param targetGuildName the name of the guild to declare war on
     * @return the result carrying the message to show the proposer
     */
    public synchronized GuildResult propose(Username leader, String targetGuildName) {
        Objects.requireNonNull(leader, "leader is required");
        @Nullable Guild proposerGuild = guildService.guildOf(leader).orElse(null);
        if (proposerGuild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!proposerGuild.isLeader(leader)) {
            return GuildResult.failure("Only the guild leader can declare a guild war.");
        }
        if (proposerGuild.isAtWar()) {
            return GuildResult.failure("Your guild is already at war. See GUILD WAR STATUS.");
        }
        if (hasOutstandingProposalFrom(proposerGuild.id())) {
            return GuildResult.failure("Your guild already has a war declaration awaiting an answer.");
        }
        String normalized = targetGuildName == null ? "" : targetGuildName.trim();
        if (normalized.isEmpty()) {
            return GuildResult.failure("Declare war on which guild? Usage: GUILD WAR <guild name>");
        }
        @Nullable Guild targetGuild = guildService.findByName(normalized).orElse(null);
        if (targetGuild == null) {
            return GuildResult.failure("No guild named '" + normalized + "' exists.");
        }
        if (targetGuild.id().equals(proposerGuild.id())) {
            return GuildResult.failure("Your guild cannot declare war on itself.");
        }
        if (targetGuild.isAtWar()) {
            return GuildResult.failure(targetGuild.name() + " is already at war with another guild.");
        }
        proposals.put(targetGuild.id(), new PendingWar(proposerGuild.id(), PROPOSAL_TIMEOUT_TICKS));
        messageBroadcaster.sendToPlayer(targetGuild.leaderId(), new PlainTextMessage(
            "[Guild War] " + proposerGuild.name() + " have declared war on your guild! GUILD WAR ACCEPT "
                + "to fight or GUILD WAR DECLINE to refuse (60s)."));
        return GuildResult.success(
            "You declare war on " + targetGuild.name() + ". Awaiting their leader's answer (60s).",
            proposerGuild);
    }

    /**
     * Accepts a pending war declaration made against the caller's guild, starting the war.
     *
     * <p>Leader-only on the accepting side. Re-validates that neither guild has since entered another
     * war before starting this one, then declares the war on both guilds (each side scoreless) and
     * fires a server-wide announcement.
     *
     * @param leader the accepting player (must be their guild's leader)
     * @return the result carrying the message to show the accepter
     */
    public synchronized GuildResult accept(Username leader) {
        Objects.requireNonNull(leader, "leader is required");
        @Nullable Guild acceptingGuild = guildService.guildOf(leader).orElse(null);
        if (acceptingGuild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!acceptingGuild.isLeader(leader)) {
            return GuildResult.failure("Only the guild leader can accept a guild war.");
        }
        @Nullable PendingWar pending = proposals.get(acceptingGuild.id());
        if (pending == null) {
            return GuildResult.failure("Your guild has no pending war declaration to accept.");
        }
        @Nullable Guild proposerGuild = guildService.findById(pending.proposerGuildId()).orElse(null);
        if (proposerGuild == null) {
            proposals.remove(acceptingGuild.id());
            return GuildResult.failure("The guild that declared war no longer exists.");
        }
        if (acceptingGuild.isAtWar()) {
            proposals.remove(acceptingGuild.id());
            return GuildResult.failure("Your guild is already at war.");
        }
        if (proposerGuild.isAtWar()) {
            proposals.remove(acceptingGuild.id());
            return GuildResult.failure(proposerGuild.name() + " is already at war with another guild.");
        }
        proposals.remove(acceptingGuild.id());
        guildService.saveWarState(proposerGuild.withActiveWar(GuildWar.against(acceptingGuild.id())));
        Guild updatedAccepting = acceptingGuild.withActiveWar(GuildWar.against(proposerGuild.id()));
        guildService.saveWarState(updatedAccepting);
        broadcastGlobal("[Guild War] War is declared! The " + proposerGuild.name() + " and the "
            + acceptingGuild.name() + " now battle for supremacy — first to " + GuildWar.POINTS_TO_WIN
            + " duel victories wins!");
        return GuildResult.success(
            "Your guild accepts the war against " + proposerGuild.name() + "! First to "
                + GuildWar.POINTS_TO_WIN + " duel wins takes it.",
            updatedAccepting);
    }

    /**
     * Declines a pending war declaration made against the caller's guild, rejecting it immediately.
     *
     * @param leader the declining player (must be their guild's leader)
     * @return the result carrying the message to show the decliner
     */
    public synchronized GuildResult decline(Username leader) {
        Objects.requireNonNull(leader, "leader is required");
        @Nullable Guild decliningGuild = guildService.guildOf(leader).orElse(null);
        if (decliningGuild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!decliningGuild.isLeader(leader)) {
            return GuildResult.failure("Only the guild leader can decline a guild war.");
        }
        @Nullable PendingWar pending = proposals.remove(decliningGuild.id());
        if (pending == null) {
            return GuildResult.failure("Your guild has no pending war declaration to decline.");
        }
        @Nullable Guild proposerGuild = guildService.findById(pending.proposerGuildId()).orElse(null);
        String proposerName = proposerGuild == null ? "The challenging guild" : proposerGuild.name();
        if (proposerGuild != null) {
            messageBroadcaster.sendToPlayer(proposerGuild.leaderId(), new PlainTextMessage(
                "[Guild War] " + decliningGuild.name() + " have declined your declaration of war."));
        }
        return GuildResult.success("You decline the war declared by " + proposerName + ".", decliningGuild);
    }

    /**
     * Concedes the caller's guild's active war as a forfeit, ending it early. The opposing guild is
     * credited the win (its {@link Guild#warWins() warWins} increments) with no war-point threshold
     * required, and a server-wide announcement fires.
     *
     * @param leader the conceding player (must be their guild's leader)
     * @return the result carrying the message to show the conceder
     */
    public synchronized GuildResult concede(Username leader) {
        Objects.requireNonNull(leader, "leader is required");
        @Nullable Guild concedingGuild = guildService.guildOf(leader).orElse(null);
        if (concedingGuild == null) {
            return GuildResult.failure("You are not in a guild.");
        }
        if (!concedingGuild.isLeader(leader)) {
            return GuildResult.failure("Only the guild leader can concede a guild war.");
        }
        @Nullable GuildWar war = concedingGuild.activeWar();
        if (war == null) {
            return GuildResult.failure("Your guild is not at war.");
        }
        Guild clearedConceding = concedingGuild.withActiveWar(null);
        guildService.saveWarState(clearedConceding);
        @Nullable Guild opponentGuild = guildService.findById(war.opponent()).orElse(null);
        if (opponentGuild == null) {
            return GuildResult.success("You concede the war.", clearedConceding);
        }
        guildService.saveWarState(opponentGuild.withActiveWar(null).withWarWin());
        broadcastGlobal("[Guild War] The " + opponentGuild.name() + " have triumphed over the "
            + concedingGuild.name() + " in the guild war! (" + concedingGuild.name() + " conceded.)");
        return GuildResult.success(
            "You concede the war. " + opponentGuild.name() + " is credited the victory.",
            clearedConceding);
    }

    /**
     * Renders the live status of the caller's guild's active war: opponent, each side's war-point
     * score, and the target to win. Used by {@code GUILD WAR STATUS} (and plain {@code GUILD WAR} while
     * a war is active). Read-only; performs no mutation.
     *
     * @param player the player asking for the status
     * @return the lines to show the player, never empty
     */
    public List<String> statusLines(Username player) {
        Objects.requireNonNull(player, "player is required");
        @Nullable Guild guild = guildService.guildOf(player).orElse(null);
        if (guild == null) {
            return List.of("You are not in a guild.");
        }
        @Nullable GuildWar war = guild.activeWar();
        if (war == null) {
            return List.of("Your guild is not currently at war. Declare one with GUILD WAR <guild>.");
        }
        @Nullable Guild opponentGuild = guildService.findById(war.opponent()).orElse(null);
        String opponentName = opponentGuild == null ? "an unknown guild" : opponentGuild.name();
        return List.of(
            "=== Guild War: " + guild.name() + " vs " + opponentName + " ===",
            "  " + guild.name() + ": " + war.ownPoints() + " war point(s)",
            "  " + opponentName + ": " + war.opponentPoints() + " war point(s)",
            "  First to " + GuildWar.POINTS_TO_WIN + " war points wins.");
    }

    /**
     * Awards a war point to the winner's guild when a consensual duel resolves between live members of
     * two guilds currently at war with each other, and ends the war (with a server-wide announcement
     * and a persistent {@code warWins} increment) when the winner reaches {@link GuildWar#POINTS_TO_WIN}.
     *
     * <p>Guild membership is read live from {@link GuildService} at this moment, not snapshotted at war
     * declaration: a player who has since left either guild no longer scores. A no-op unless both
     * players are in different guilds that are mutually at war. Runs on the tick thread from the
     * duel-resolution choke point (AGENTS.md §5).
     *
     * @param winner the duel survivor
     * @param loser  the defeated duellist
     */
    public synchronized void recordDuelWin(Username winner, Username loser) {
        Objects.requireNonNull(winner, "winner is required");
        Objects.requireNonNull(loser, "loser is required");
        @Nullable Guild winnerGuild = guildService.guildOf(winner).orElse(null);
        @Nullable Guild loserGuild = guildService.guildOf(loser).orElse(null);
        if (winnerGuild == null || loserGuild == null) {
            return;
        }
        if (winnerGuild.id().equals(loserGuild.id())) {
            return;
        }
        @Nullable GuildWar winnerWar = winnerGuild.activeWar();
        @Nullable GuildWar loserWar = loserGuild.activeWar();
        if (winnerWar == null || loserWar == null) {
            return;
        }
        if (!winnerWar.opponent().equals(loserGuild.id()) || !loserWar.opponent().equals(winnerGuild.id())) {
            return;
        }
        GuildWar updatedWinnerWar = winnerWar.withOwnPoint();
        GuildWar updatedLoserWar = loserWar.withOpponentPoint();
        if (updatedWinnerWar.isWonByHolder()) {
            guildService.saveWarState(winnerGuild.withActiveWar(null).withWarWin());
            guildService.saveWarState(loserGuild.withActiveWar(null));
            broadcastGlobal("[Guild War] The " + winnerGuild.name() + " have triumphed over the "
                + loserGuild.name() + " in the guild war!");
            return;
        }
        guildService.saveWarState(winnerGuild.withActiveWar(updatedWinnerWar));
        guildService.saveWarState(loserGuild.withActiveWar(updatedLoserWar));
        announceScore(winnerGuild, loserGuild, winner, updatedWinnerWar);
    }

    @Override
    public synchronized void tick() {
        proposals.replaceAll((target, pending) -> pending.tickDown());
        proposals.values().removeIf(PendingWar::expired);
    }

    // ── internals ─────────────────────────────────────────────────────

    private boolean hasOutstandingProposalFrom(GuildId proposerGuildId) {
        return proposals.values().stream()
            .anyMatch(pending -> pending.proposerGuildId().equals(proposerGuildId));
    }

    private void announceScore(Guild winnerGuild, Guild loserGuild, Username winner, GuildWar winnerWar) {
        String line = "[Guild War] " + winner.getValue() + " wins a duel for " + winnerGuild.name()
            + "! " + winnerGuild.name() + " " + winnerWar.ownPoints() + " - " + winnerWar.opponentPoints()
            + " " + loserGuild.name() + ".";
        PlainTextMessage payload = new PlainTextMessage(line);
        for (GuildMember member : winnerGuild.members()) {
            messageBroadcaster.sendToPlayer(member.username(), payload);
        }
        for (GuildMember member : loserGuild.members()) {
            messageBroadcaster.sendToPlayer(member.username(), payload);
        }
    }

    private void broadcastGlobal(String message) {
        messageBroadcaster.broadcastGlobal(new PlainTextMessage(message), Set.of());
    }
}
