package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.ItemSet;
import io.taanielo.jmud.core.world.ItemSetId;
import io.taanielo.jmud.core.world.ItemSetThreshold;

/**
 * Maps between {@link ItemSetDto} (persistence shape) and {@link ItemSet} (domain shape), keeping
 * Jackson concerns out of the domain model (AGENTS.md §3.2).
 */
public class ItemSetMapper {

    /**
     * Converts a loaded DTO into a validated domain {@link ItemSet}.
     *
     * @param dto the DTO read from disk
     * @return the domain item set
     */
    public ItemSet toDomain(ItemSetDto dto) {
        Objects.requireNonNull(dto, "Item set DTO is required");
        List<ItemId> pieces = Objects.requireNonNull(dto.pieces(), "Item set pieces are required").stream()
            .map(ItemId::of)
            .toList();
        List<ItemSetThreshold> thresholds =
            Objects.requireNonNull(dto.thresholds(), "Item set thresholds are required").stream()
                .map(t -> new ItemSetThreshold(t.piecesRequired(), t.stats()))
                .toList();
        return new ItemSet(ItemSetId.of(dto.id()), dto.name(), pieces, thresholds);
    }
}
