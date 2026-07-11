package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;

import io.taanielo.jmud.core.output.PlainTextStyler;
import io.taanielo.jmud.core.output.TextStyler;
import io.taanielo.jmud.core.world.Item;

/**
 * Pure, network-free helper that formats a side-by-side gear comparison for the
 * {@code compare} command.
 *
 * <p>Given a candidate {@link Item} and the item (if any) currently equipped in the candidate's
 * slot, it renders the effective stats of both — base attributes plus affix bonuses, resolved
 * through the supplied {@code effectiveStats} function — with a per-stat delta so a player can
 * instantly tell whether a swap is an upgrade. Weight, value, and (when either item is breakable)
 * durability are also shown for both items.
 *
 * <p>The delta and arrow are always read <em>equipped &rarr; candidate</em>, i.e. they describe the
 * change that would result from equipping the candidate in place of what is currently worn.
 *
 * <p>Kept free of any I/O so the diff logic can be unit-tested in isolation (AGENTS.md &sect;10).
 */
public final class ItemComparison {

    private static final TextStyler PLAIN = new PlainTextStyler();

    private ItemComparison() {
    }

    /**
     * Formats a comparison without rarity coloring.
     *
     * @param candidate      the item being considered
     * @param equipped       the item currently worn in the candidate's slot, or empty when the slot
     *                       is free
     * @param effectiveStats resolves an item to its effective stats (base attributes plus affix
     *                       bonuses)
     * @return the lines to render, never empty
     */
    public static List<String> format(
        Item candidate, Optional<Item> equipped, Function<Item, Map<String, Integer>> effectiveStats) {
        return format(candidate, equipped, effectiveStats, PLAIN);
    }

    /**
     * Formats a comparison, coloring each item name by its rarity tier through the supplied
     * {@link TextStyler}.
     *
     * @param candidate      the item being considered
     * @param equipped       the item currently worn in the candidate's slot, or empty when the slot
     *                       is free
     * @param effectiveStats resolves an item to its effective stats (base attributes plus affix
     *                       bonuses)
     * @param styler         the styler used to color item names by rarity
     * @return the lines to render, never empty
     */
    public static List<String> format(
        Item candidate,
        Optional<Item> equipped,
        Function<Item, Map<String, Integer>> effectiveStats,
        TextStyler styler) {
        Objects.requireNonNull(candidate, "Candidate item is required");
        Objects.requireNonNull(equipped, "Equipped optional is required");
        Objects.requireNonNull(effectiveStats, "Effective-stats resolver is required");
        Objects.requireNonNull(styler, "Styler is required");

        return equipped.isPresent()
            ? formatOccupied(candidate, equipped.get(), effectiveStats, styler)
            : formatEmptySlot(candidate, effectiveStats, styler);
    }

    private static List<String> formatEmptySlot(
        Item candidate, Function<Item, Map<String, Integer>> effectiveStats, TextStyler styler) {
        List<String> lines = new ArrayList<>();
        lines.add("Comparing " + styler.rarity(candidate.getName(), candidate.getRarity()) + ":");
        lines.add("Slot: " + candidate.getEquipSlot().id());
        lines.add("Nothing is currently equipped in that slot — just EQUIP it.");
        Map<String, Integer> stats = effectiveStats.apply(candidate);
        if (stats.isEmpty()) {
            lines.add("Effective stats: (none)");
        } else {
            lines.add("Effective stats:");
            new TreeSet<>(stats.keySet()).forEach(stat ->
                lines.add(String.format("  %-12s : %d", stat, stats.getOrDefault(stat, 0))));
        }
        lines.add(String.format("  %-12s : %d", "weight", candidate.getWeight()));
        lines.add(String.format("  %-12s : %d", "value", candidate.getValue()));
        if (candidate.isBreakable()) {
            lines.add(String.format("  %-12s : %s", "durability", durability(candidate)));
        }
        return List.copyOf(lines);
    }

    private static List<String> formatOccupied(
        Item candidate,
        Item equipped,
        Function<Item, Map<String, Integer>> effectiveStats,
        TextStyler styler) {
        List<String> lines = new ArrayList<>();
        lines.add("Comparing " + styler.rarity(candidate.getName(), candidate.getRarity())
            + " (candidate) to " + styler.rarity(equipped.getName(), equipped.getRarity())
            + " (equipped):");
        lines.add("Slot: " + candidate.getEquipSlot().id());
        lines.add("Values shown as equipped -> candidate (delta).");

        Map<String, Integer> candidateStats = effectiveStats.apply(candidate);
        Map<String, Integer> equippedStats = effectiveStats.apply(equipped);
        TreeSet<String> statNames = new TreeSet<>();
        statNames.addAll(candidateStats.keySet());
        statNames.addAll(equippedStats.keySet());
        if (statNames.isEmpty()) {
            lines.add("Effective stats: (none)");
        } else {
            lines.add("Effective stats:");
            for (String stat : statNames) {
                int equippedValue = equippedStats.getOrDefault(stat, 0);
                int candidateValue = candidateStats.getOrDefault(stat, 0);
                lines.add(String.format("  %-12s : %d -> %d (%s)",
                    stat, equippedValue, candidateValue, delta(candidateValue - equippedValue)));
            }
        }
        lines.add(diffLine("weight", equipped.getWeight(), candidate.getWeight()));
        lines.add(diffLine("value", equipped.getValue(), candidate.getValue()));
        if (candidate.isBreakable() || equipped.isBreakable()) {
            lines.add(String.format("  %-12s : %s -> %s",
                "durability", durability(equipped), durability(candidate)));
        }
        return List.copyOf(lines);
    }

    private static String diffLine(String label, int equippedValue, int candidateValue) {
        return String.format("  %-12s : %d -> %d (%s)",
            label, equippedValue, candidateValue, delta(candidateValue - equippedValue));
    }

    private static String delta(int value) {
        return value == 0 ? "0" : String.format("%+d", value);
    }

    private static String durability(Item item) {
        if (!item.isBreakable()) {
            return "n/a";
        }
        int max = item.getMaxDurability();
        int current = item.getDurability() == null ? max : item.getDurability();
        return current + "/" + max;
    }
}
