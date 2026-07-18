package io.taanielo.jmud.core.guild;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;

/**
 * Domain service governing the cooperative guild-quest system.
 *
 * <p>Every guild has exactly one active {@link GuildQuest} — a shared "slay N of a mob type" objective
 * drawn from a level-banded {@link GuildQuestPool}. Progress accrues automatically for the whole guild
 * whenever any online member kills the matching mob type ({@link #recordKill}), mirroring how
 * {@code QuestKillService} credits a personal kill quest on death; members never ACCEPT anything. On
 * completion the reward gold is paid straight into the guild treasury (which also advances its
 * {@link GuildLevel}), a guild-wide announcement fires on the {@code [Guild]} channel, and a fresh
 * objective is rolled immediately — so a completed quest can never be double-credited.
 *
 * <p>Assignment is deterministic from a single {@link #rotationCounter}, advanced once per game day by
 * {@link GuildQuestRotationTicker} (the same cadence as the daily-quest rotation). All guild-state
 * mutation runs on the tick thread via {@link GuildService}; the counter is confined to the tick thread
 * for writes and safe to read as a point-in-time snapshot (AGENTS.md §5).
 */
public class GuildQuestService {

    private final GuildService guildService;
    private final GuildQuestPool pool;
    private final MessageBroadcaster messageBroadcaster;
    private final AtomicLong rotationCounter = new AtomicLong();

    /**
     * Creates the guild-quest service.
     *
     * @param guildService       the authoritative owner of guild state and persistence
     * @param pool               the level-banded catalogue of assignable guild-quest objectives
     * @param messageBroadcaster the sanctioned fan-out used to announce completions and rotations
     */
    public GuildQuestService(
        GuildService guildService,
        GuildQuestPool pool,
        MessageBroadcaster messageBroadcaster
    ) {
        this.guildService = Objects.requireNonNull(guildService, "guildService is required");
        this.pool = Objects.requireNonNull(pool, "pool is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "messageBroadcaster is required");
    }

    /**
     * Returns the active guild quest for the guild the given player belongs to, lazily assigning one
     * (and persisting it) when the guild has none yet. Returns empty when the player is in no guild.
     *
     * @param member the player whose guild's quest to resolve; must not be null
     * @return the guild's active quest, or empty when the player is guildless
     */
    public Optional<GuildQuest> activeQuestFor(Username member) {
        Objects.requireNonNull(member, "member is required");
        @Nullable Guild guild = guildService.guildOf(member).orElse(null);
        if (guild == null) {
            return Optional.empty();
        }
        Guild assigned = ensureAssigned(guild);
        return Optional.ofNullable(assigned.activeGuildQuest());
    }

    /**
     * Credits a mob kill by {@code killer} toward their guild's shared guild quest, if any. When the
     * kill completes the objective, the reward is deposited into the treasury, a {@code [Guild]}
     * announcement is sent to every online member, and a fresh objective is rolled. A no-op when the
     * killer is guildless or the killed mob does not match the active objective.
     *
     * <p>This runs on the tick thread from the mob-death path, alongside the personal
     * {@code QuestKillService} credit; the two are independent so a guild kill never touches a player's
     * personal quest slots.
     *
     * @param killer      the guild member who landed the kill; must not be null
     * @param killedMobId the template id of the mob that was just killed; must not be null
     */
    public void recordKill(Username killer, String killedMobId) {
        Objects.requireNonNull(killer, "killer is required");
        Objects.requireNonNull(killedMobId, "killedMobId is required");

        @Nullable Guild guild = guildService.guildOf(killer).orElse(null);
        if (guild == null) {
            return;
        }
        guild = ensureAssigned(guild);
        GuildQuest quest = Objects.requireNonNull(guild.activeGuildQuest());
        if (!quest.targetMobId().equals(killedMobId)) {
            return;
        }
        GuildQuest progressed = quest.recordKill();
        if (progressed.isComplete()) {
            completeAndReassign(guild, progressed);
        } else {
            guildService.saveGuildQuestState(guild.withActiveGuildQuest(progressed));
        }
    }

    /**
     * Advances the rotation by one game day and re-rolls every guild's active guild quest to a fresh,
     * level-appropriate objective (resetting in-progress counters), announcing the new objective to each
     * guild. Incomplete objectives are simply replaced — only an actual completion pays a reward. Must
     * only be called on the tick thread.
     */
    public void rotate() {
        rotationCounter.incrementAndGet();
        for (Guild guild : guildService.allGuilds()) {
            GuildQuest next = assignObjectiveFor(guild);
            Guild updated = guild.withActiveGuildQuest(next);
            guildService.saveGuildQuestState(updated);
            announceNewObjective(updated, next);
        }
    }

    /** Returns the current rotation counter value (the number of day transitions applied). */
    public long rotationCounter() {
        return rotationCounter.get();
    }

    // ── internals ─────────────────────────────────────────────────────

    private Guild ensureAssigned(Guild guild) {
        @Nullable GuildQuest current = guild.activeGuildQuest();
        if (current != null && poolContains(current.questId())) {
            return guild;
        }
        GuildQuest assigned = assignObjectiveFor(guild);
        Guild updated = guild.withActiveGuildQuest(assigned);
        guildService.saveGuildQuestState(updated);
        return updated;
    }

    private void completeAndReassign(Guild guild, GuildQuest completed) {
        int reward = completed.goldReward();
        GuildQuest next = assignObjectiveFor(guild);
        Guild updated = guild.depositTreasury(reward).withActiveGuildQuest(next);
        guildService.saveGuildQuestState(updated);
        announce(updated,
            "[Guild] Guild quest complete: " + completed.name() + "! " + reward
                + " gold has been paid into the treasury.");
        announceNewObjective(updated, next);
    }

    private boolean poolContains(String questId) {
        return pool.objectives().stream().anyMatch(o -> o.questId().equals(questId));
    }

    /**
     * Selects a fresh guild quest for the guild: the objective at the current rotation index within the
     * band of objectives eligible for the guild's level. Visible for testing.
     *
     * @param guild the guild to assign an objective to
     * @return a zero-progress guild quest for a level-appropriate objective
     */
    GuildQuest assignObjectiveFor(Guild guild) {
        List<GuildQuestObjective> candidates = pool.objectivesUpToLevel(guild.level().rank());
        int index = (int) (rotationCounter.get() % candidates.size());
        return GuildQuest.fromObjective(candidates.get(index));
    }

    private void announceNewObjective(Guild guild, GuildQuest quest) {
        announce(guild,
            "[Guild] A new guild quest has been posted: " + quest.name() + " — slay "
                + quest.requiredKills() + " " + quest.targetName() + " for "
                + quest.goldReward() + " treasury gold.");
    }

    private void announce(Guild guild, String message) {
        PlainTextMessage payload = new PlainTextMessage(message);
        for (GuildMember member : guild.members()) {
            messageBroadcaster.sendToPlayer(member.username(), payload);
        }
    }
}
