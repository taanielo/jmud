package io.taanielo.jmud.core.guild;

import java.util.Objects;

import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.TimeOfDay;
import io.taanielo.jmud.core.world.WorldClock;

/**
 * World-level {@link Tickable} that pays passive, level-scaled treasury interest to every guild once per
 * game day (issue #773).
 *
 * <p>The {@link WorldClock} flips deterministically between {@link TimeOfDay#DAY} and
 * {@link TimeOfDay#NIGHT}; a new day is the transition from {@code NIGHT} back to {@code DAY} — the same
 * boundary {@code GuildQuestRotationTicker}/{@code DailyQuestRotationTicker} use. On that boundary this
 * ticker credits each guild's treasury with {@link Guild#dailyTreasuryInterest()} (a level-scaled cut of
 * its <em>current</em> balance) and announces the amount to every online member on the {@code [Guild]}
 * channel. Interest is driven purely by tick-derived time, never the wall clock (AGENTS.md §5).
 *
 * <p>Interest is credited to {@link Guild#treasuryGold()} only, via {@link Guild#creditTreasuryInterest}
 * — never {@link Guild#depositTreasury}, which would also bump {@link Guild#lifetimeDepositedGold()} and
 * so let interest drive leveling. A guild whose computed interest is zero (an empty or sub-threshold
 * treasury) is skipped entirely, so empty or inactive guilds generate no announcement spam.
 *
 * <p>Register this ticker <em>after</em> the {@link WorldClock} so that on the boundary tick the clock has
 * already flipped to {@code DAY} before this ticker observes it. All guild-state mutation runs on the tick
 * thread via {@link GuildService#saveInterestState} (AGENTS.md §5).
 */
public class GuildInterestTicker implements Tickable {

    private final WorldClock worldClock;
    private final GuildService guildService;
    private final MessageBroadcaster messageBroadcaster;
    private TimeOfDay previousTimeOfDay;

    /**
     * Creates the interest ticker.
     *
     * @param worldClock         the deterministic day/night clock whose transitions drive interest
     * @param guildService       the authoritative owner of guild state and persistence
     * @param messageBroadcaster the sanctioned fan-out used to announce interest to online members
     */
    public GuildInterestTicker(
        WorldClock worldClock, GuildService guildService, MessageBroadcaster messageBroadcaster) {
        this.worldClock = Objects.requireNonNull(worldClock, "worldClock is required");
        this.guildService = Objects.requireNonNull(guildService, "guildService is required");
        this.messageBroadcaster =
            Objects.requireNonNull(messageBroadcaster, "messageBroadcaster is required");
        this.previousTimeOfDay = worldClock.timeOfDay();
    }

    /**
     * Detects a night-to-day transition and, when one occurs, credits every guild's treasury with its
     * level-scaled daily interest. Must only be called on the tick thread.
     */
    @Override
    public void tick() {
        TimeOfDay current = worldClock.timeOfDay();
        boolean newDay = previousTimeOfDay == TimeOfDay.NIGHT && current == TimeOfDay.DAY;
        previousTimeOfDay = current;
        if (!newDay) {
            return;
        }
        for (Guild guild : guildService.allGuilds()) {
            int interest = guild.dailyTreasuryInterest();
            if (interest <= 0) {
                continue;
            }
            Guild credited = guild.creditTreasuryInterest(interest);
            guildService.saveInterestState(credited);
            announce(credited, interest);
        }
    }

    private void announce(Guild guild, int interest) {
        GuildLevel level = guild.level();
        String message = "[Guild] The " + guild.name() + " treasury earns " + interest
            + " gold in interest (" + level.interestRatePercent() + "% at guild level " + level.rank()
            + "). Balance: " + guild.treasuryGold() + " gold.";
        PlainTextMessage payload = new PlainTextMessage(message);
        for (GuildMember member : guild.members()) {
            messageBroadcaster.sendToPlayer(member.username(), payload);
        }
    }
}
