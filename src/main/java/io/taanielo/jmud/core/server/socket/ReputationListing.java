package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.PlayerReputation;
import io.taanielo.jmud.core.faction.ReputationService;

/**
 * Pure, network-free helper that formats a player's faction reputation standings for the
 * {@code REPUTATION} command.
 *
 * <p>Kept free of any I/O so the ordering and label derivation can be unit tested in isolation. Only
 * factions with a tracked (non-zero) standing are shown, sorted by faction display name for a stable,
 * deterministic order. Each faction's display name and Hostile/Neutral/Friendly label are resolved
 * from the already-loaded {@link ReputationService} faction definitions (AGENTS.md §5 — no I/O).
 */
public final class ReputationListing {

    private static final String HEADER = "Faction reputation:";
    private static final String NONE =
        "You have not yet made a name for yourself with any faction.";

    private ReputationListing() {
    }

    /**
     * Formats the given reputation into display lines.
     *
     * @param reputation        the player's reputation standings; must not be null
     * @param reputationService resolves faction display names and standing labels; must not be null
     * @return the lines to render, never empty
     */
    public static List<String> format(PlayerReputation reputation, ReputationService reputationService) {
        Objects.requireNonNull(reputation, "reputation is required");
        Objects.requireNonNull(reputationService, "reputationService is required");

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<FactionId, Integer> standing : reputation.standings().entrySet()) {
            int value = standing.getValue();
            if (value == 0) {
                continue;
            }
            FactionId factionId = standing.getKey();
            String name = reputationService.findFaction(factionId)
                .map(faction -> faction.name())
                .orElse(factionId.getValue());
            String label = reputationService.standingLabel(reputation, factionId);
            entries.add(new Entry(name, value, label));
        }

        if (entries.isEmpty()) {
            return List.of(NONE);
        }

        entries.sort(Comparator.comparing(Entry::name));
        List<String> lines = new ArrayList<>(entries.size() + 1);
        lines.add(HEADER);
        for (Entry entry : entries) {
            lines.add(String.format(
                "  %-28s %+5d  (%s)", entry.name(), entry.value(), entry.label()));
        }
        return List.copyOf(lines);
    }

    private record Entry(String name, int value, String label) {
    }
}
