package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.tick.TickMetricsService;
import io.taanielo.jmud.core.tick.TickStatsSummary;

/**
 * Handles the wizard-only {@code STATS} command, which reports tick-loop health.
 *
 * <p>The command reads aggregated metrics from {@link TickMetricsService} (a domain service) and
 * renders them as a small table. It executes on the tick thread via the player command queue, the
 * same thread that records the metrics, so no synchronisation is needed (AGENTS.md §5). Access is
 * gated by {@link WizardPolicy}; non-wizards receive a denial message.
 */
public class StatsCommand extends RegistrableCommand {

    private final TickMetricsService metricsService;
    private final WizardPolicy wizardPolicy;

    /**
     * Creates the STATS command.
     *
     * @param registry       the command registry to register with
     * @param metricsService the tick metrics aggregation service to query
     * @param wizardPolicy   the policy deciding which players may view stats
     */
    public StatsCommand(SocketCommandRegistry registry, TickMetricsService metricsService, WizardPolicy wizardPolicy) {
        super(registry);
        this.metricsService = Objects.requireNonNull(metricsService, "Metrics service is required");
        this.wizardPolicy = Objects.requireNonNull(wizardPolicy, "Wizard policy is required");
    }

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public String shortDescription() {
        return "Show tick-loop health metrics (wizard only).";
    }

    @Override
    public String longDescription() {
        return """
               Usage: STATS
                 Displays aggregated tick-loop performance: average and maximum tick duration,
                 the slowest Tickable by aggregate cost, tick overruns and total uptime ticks.
                 Restricted to wizards.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"STATS".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, this::handleStats));
    }

    private void handleStats(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to view stats.");
            return;
        }
        Player player = context.getPlayer();
        if (!wizardPolicy.isWizard(player)) {
            context.writeLineWithPrompt("Denied. The STATS command is restricted to wizards.");
            return;
        }
        for (String line : format(metricsService.getSummary())) {
            context.writeLineSafe(line);
        }
        context.sendPrompt();
    }

    /**
     * Renders a {@link TickStatsSummary} as tabular lines for display. Kept package-private and
     * static so it is unit-testable without networking.
     *
     * @param summary the aggregated tick statistics
     * @return the formatted lines, one per output row
     */
    static List<String> format(TickStatsSummary summary) {
        List<String> lines = new ArrayList<>();
        lines.add("=== Tick Health ===");
        lines.add("Total ticks: " + summary.totalTicksRecorded());
        lines.add(String.format(
            Locale.ROOT,
            "Avg duration: %s ms | Max: %s ms | Overruns: %d (last %d ticks)",
            millis(summary.averageDurationNanos()),
            millis((double) summary.maxDurationNanos()),
            summary.overrunCount(),
            summary.windowTicks()
        ));
        String slowest = summary.slowestTickableName();
        if (slowest == null) {
            lines.add("Slowest Tickable: (none recorded)");
        } else {
            lines.add(String.format(
                Locale.ROOT,
                "Slowest Tickable: %s (%s ms / %d invocations)",
                slowest,
                millis((double) summary.slowestTickableTotalNanos()),
                summary.slowestTickableInvocations()
            ));
        }
        return lines;
    }

    private static String millis(double nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0);
    }
}
