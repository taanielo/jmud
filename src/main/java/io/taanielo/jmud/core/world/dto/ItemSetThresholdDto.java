package io.taanielo.jmud.core.world.dto;

import java.util.Map;

/**
 * Persistence DTO for a single item-set threshold. Field names map to the {@code pieces_required}
 * and {@code stats} JSON keys via the shared snake-case {@link JsonDataMapper} configuration.
 *
 * @param piecesRequired number of worn set pieces at or above which the bonus applies
 * @param stats          the additive stat bonuses granted while the threshold is met
 */
public record ItemSetThresholdDto(int piecesRequired, Map<String, Integer> stats) {
}
