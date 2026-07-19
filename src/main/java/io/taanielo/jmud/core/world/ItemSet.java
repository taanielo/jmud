package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Objects;

/**
 * A named group of matching gear pieces that grants stacking additive stat bonuses once enough of
 * its pieces are worn together (issue #771). An item set is pure content data: its bonuses are
 * computed live from a player's currently-equipped items every stat read (never persisted) by
 * {@link io.taanielo.jmud.core.combat.SetBonusResolver}.
 *
 * @param id         unique id of the set, matched against member items' {@code set_id}
 * @param name       human-readable display name (e.g. {@code "Wayfarer's Leathers"})
 * @param pieceIds   ordered ids of the 2-5 items that make up the set
 * @param thresholds one or more {@link ItemSetThreshold}s, each awarding its stats once its
 *                   piece count is worn; ordered by {@code piecesRequired} ascending
 */
public record ItemSet(ItemSetId id, String name, List<ItemId> pieceIds, List<ItemSetThreshold> thresholds) {

    public ItemSet {
        Objects.requireNonNull(id, "Item set id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item set name must not be blank");
        }
        Objects.requireNonNull(pieceIds, "Item set pieces are required");
        if (pieceIds.size() < 2) {
            throw new IllegalArgumentException("An item set must list at least 2 pieces");
        }
        if (pieceIds.size() != pieceIds.stream().distinct().count()) {
            throw new IllegalArgumentException("An item set must not list a piece more than once");
        }
        Objects.requireNonNull(thresholds, "Item set thresholds are required");
        if (thresholds.isEmpty()) {
            throw new IllegalArgumentException("An item set must define at least one threshold");
        }
        for (ItemSetThreshold threshold : thresholds) {
            if (threshold.piecesRequired() > pieceIds.size()) {
                throw new IllegalArgumentException(
                    "Item set threshold pieces_required " + threshold.piecesRequired()
                        + " exceeds the set's " + pieceIds.size() + " pieces");
            }
        }
        pieceIds = List.copyOf(pieceIds);
        thresholds = thresholds.stream()
            .sorted((a, b) -> Integer.compare(a.piecesRequired(), b.piecesRequired()))
            .toList();
    }

    /**
     * Returns the total number of pieces that make up the complete set.
     *
     * @return the piece count (always at least 2)
     */
    public int pieceCount() {
        return pieceIds.size();
    }

    /**
     * Returns whether the given item id is one of this set's pieces.
     *
     * @param itemId the item id to test
     * @return {@code true} when the item is a member of this set
     */
    public boolean containsPiece(ItemId itemId) {
        return pieceIds.contains(Objects.requireNonNull(itemId, "Item id is required"));
    }
}
