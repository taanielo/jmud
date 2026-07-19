package io.taanielo.jmud.core.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.ItemSet;
import io.taanielo.jmud.core.world.ItemSetId;
import io.taanielo.jmud.core.world.ItemSetThreshold;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.ItemSetRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Resolves the additive stat bonuses a player earns from wearing matching pieces of an item set
 * (issue #771).
 *
 * <p>Modeled directly on {@link EquipmentArmorResolver}: it is read-only over a player's
 * {@link PlayerEquipment} plus the {@link ItemRepository}/{@link ItemSetRepository}, performs no
 * tick-thread blocking I/O beyond the same cached repository lookups armour resolution already does,
 * and never mutates or persists anything. Set bonuses are recomputed live on every read, so swapping
 * a piece out immediately drops the corresponding threshold bonus with no cached state.
 *
 * <p>For each set the player has at least one piece of equipped, every {@link ItemSetThreshold}
 * whose {@code pieces_required} is met by the number of worn pieces contributes its stats
 * additively; multiple met thresholds stack. {@link #bonusStats(Player)} aggregates these across all
 * worn sets (feeding, for example, the {@code "ac"} key into {@link EquipmentArmorResolver}), while
 * {@link #activeSets(Player)} exposes per-set progress for display in EQUIPMENT, SCORE and EXAMINE.
 */
public class SetBonusResolver {

    private final ItemRepository itemRepository;
    private final ItemSetRepository itemSetRepository;

    /**
     * Creates a resolver backed by the given item and item-set repositories.
     *
     * @param itemRepository    repository used to load equipped item definitions (and their set ids)
     * @param itemSetRepository repository used to load item-set definitions
     */
    public SetBonusResolver(ItemRepository itemRepository, ItemSetRepository itemSetRepository) {
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        this.itemSetRepository = Objects.requireNonNull(itemSetRepository, "Item set repository is required");
    }

    /**
     * Returns the total additive stat bonuses granted by every set the player currently has enough
     * pieces of, keyed by stat name (e.g. {@code "ac"}). Empty when no set threshold is met.
     *
     * @param player the player whose equipment is inspected
     * @return the aggregated set stat bonuses; never null
     */
    public Map<String, Integer> bonusStats(Player player) {
        Objects.requireNonNull(player, "Player is required");
        Map<String, Integer> total = new LinkedHashMap<>();
        for (SetProgress progress : activeSets(player)) {
            progress.activeStats().forEach((key, value) -> total.merge(key, value, Integer::sum));
        }
        return total;
    }

    /**
     * Returns per-set progress for every set the player has at least one piece of equipped, ordered
     * by set id. Each entry reports how many pieces are worn, the currently active bonus, and the
     * next unmet threshold (when any) so the player can plan acquisitions.
     *
     * @param player the player whose equipment is inspected
     * @return the active set-progress entries; never null, empty when no set piece is worn
     */
    public List<SetProgress> activeSets(Player player) {
        Objects.requireNonNull(player, "Player is required");
        PlayerEquipment equipment = player.getEquipment();
        Map<ItemSetId, ItemSet> sets = new LinkedHashMap<>();
        Map<ItemSetId, Integer> counts = new LinkedHashMap<>();

        for (Map.Entry<EquipmentSlot, ItemId> entry : equipment.slots().entrySet()) {
            ItemId itemId = entry.getValue();
            if (itemId == null) {
                continue;
            }
            ItemSetId setId = setIdOf(itemId);
            if (setId == null) {
                continue;
            }
            ItemSet set = sets.computeIfAbsent(setId, this::loadSet);
            if (set == null || !set.containsPiece(itemId)) {
                continue;
            }
            counts.merge(setId, 1, Integer::sum);
        }

        List<SetProgress> progress = new ArrayList<>();
        for (Map.Entry<ItemSetId, Integer> entry : counts.entrySet()) {
            ItemSet set = sets.get(entry.getKey());
            if (set != null) {
                progress.add(toProgress(set, entry.getValue()));
            }
        }
        progress.sort((a, b) -> a.setId().getValue().compareTo(b.setId().getValue()));
        return progress;
    }

    private SetProgress toProgress(ItemSet set, int worn) {
        Map<String, Integer> active = new TreeMap<>();
        ItemSetThreshold next = null;
        for (ItemSetThreshold threshold : set.thresholds()) {
            if (worn >= threshold.piecesRequired()) {
                threshold.stats().forEach((key, value) -> active.merge(key, value, Integer::sum));
            } else if (next == null) {
                next = threshold;
            }
        }
        return new SetProgress(set.id(), set.name(), worn, set.pieceCount(), active, next);
    }

    /**
     * Returns human-readable lines naming the item set the given item belongs to and the pieces
     * (with their equip slots) that complete it, for display by {@code EXAMINE}. Empty when the item
     * belongs to no set or the set cannot be resolved.
     *
     * @param item the item being examined
     * @return the set-membership description lines; never null
     */
    public List<String> describeSetMembership(Item item) {
        Objects.requireNonNull(item, "Item is required");
        ItemSetId setId = item.getSetId();
        if (setId == null) {
            return List.of();
        }
        ItemSet set = loadSet(setId);
        if (set == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add("Set: " + set.name() + " (" + set.pieceCount() + " pieces)");
        for (ItemSetThreshold threshold : set.thresholds()) {
            lines.add("  " + threshold.piecesRequired() + "pc bonus: " + formatStats(threshold.stats()));
        }
        String pieces = set.pieceIds().stream()
            .map(this::pieceLabel)
            .collect(Collectors.joining(", "));
        lines.add("  Completed by: " + pieces);
        return lines;
    }

    private String pieceLabel(ItemId itemId) {
        Item piece;
        try {
            piece = itemRepository.findById(itemId).orElse(null);
        } catch (RepositoryException e) {
            piece = null;
        }
        if (piece == null) {
            return itemId.getValue();
        }
        EquipmentSlot slot = piece.getEquipSlot();
        String name = piece.getName();
        return slot == null ? name : name + " (" + slot.id() + ")";
    }

    private @Nullable ItemSetId setIdOf(ItemId itemId) {
        try {
            return itemRepository.findById(itemId).map(Item::getSetId).orElse(null);
        } catch (RepositoryException e) {
            return null;
        }
    }

    private @Nullable ItemSet loadSet(ItemSetId setId) {
        try {
            return itemSetRepository.findById(setId).orElse(null);
        } catch (RepositoryException e) {
            return null;
        }
    }

    private static String formatStats(Map<String, Integer> stats) {
        return new TreeMap<>(stats).entrySet().stream()
            .map(e -> (e.getValue() >= 0 ? "+" : "") + e.getValue() + " " + statLabel(e.getKey()))
            .collect(Collectors.joining(", "));
    }

    private static String statLabel(String key) {
        if ("ac".equals(key)) {
            return "AC";
        }
        String spaced = key.replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    /**
     * Progress toward one item set the player has at least one piece of worn.
     *
     * @param setId       the set's id
     * @param displayName the set's display name
     * @param wornPieces  how many of the set's pieces are currently equipped
     * @param totalPieces the total number of pieces in the complete set
     * @param activeStats the additive bonus currently granted (aggregate of all met thresholds);
     *                    empty when no threshold is met yet
     * @param next        the next unmet threshold the player is progressing toward, or {@code null}
     *                    when the full set (or its highest threshold) is already met
     */
    public record SetProgress(
        ItemSetId setId,
        String displayName,
        int wornPieces,
        int totalPieces,
        Map<String, Integer> activeStats,
        @Nullable ItemSetThreshold next
    ) {
        public SetProgress {
            Objects.requireNonNull(setId, "Set id is required");
            Objects.requireNonNull(displayName, "Display name is required");
            activeStats = Map.copyOf(Objects.requireNonNull(activeStats, "Active stats are required"));
        }

        /**
         * Renders a one-line progress summary, e.g.
         * {@code "Wayfarer's Leathers (2/3) - 2pc: +2 AC"} when a threshold is met, or
         * {@code "Wayfarer's Leathers (1/3) - next 2pc: +2 AC"} while progressing toward one.
         *
         * @return the formatted display line
         */
        public String describe() {
            String header = String.format("%s (%d/%d)", displayName, wornPieces, totalPieces);
            if (!activeStats.isEmpty()) {
                return header + " - " + wornPieces + "pc: " + formatStats(activeStats);
            }
            if (next != null) {
                return header + " - next " + next.piecesRequired() + "pc: " + formatStats(next.stats());
            }
            return header;
        }
    }

    /**
     * Returns a no-op resolver that reports no sets and no bonuses, for test contexts where item-set
     * data is unavailable.
     *
     * @return a resolver that always returns empty results
     */
    public static SetBonusResolver noOp() {
        ItemRepository items = new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                return Optional.empty();
            }
        };
        ItemSetRepository sets = new ItemSetRepository() {
            @Override
            public Optional<ItemSet> findById(ItemSetId id) {
                return Optional.empty();
            }

            @Override
            public List<ItemSet> findAll() {
                return List.of();
            }
        };
        return new SetBonusResolver(items, sets);
    }
}
