package io.taanielo.jmud.core.character.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON DTO for a signed core-attribute bonus block (race bonuses or a class's creation bonuses).
 * Every field is optional and defaults to {@code 0} when omitted. The {@code int} key is mapped to
 * the {@code intellect} component because {@code int} is a reserved Java word.
 *
 * @param str signed strength delta
 * @param intellect signed intellect delta (JSON key {@code int})
 * @param wis signed wisdom delta
 * @param agi signed agility delta
 */
public record AttributeBonusDto(
    int str,
    @JsonProperty("int") int intellect,
    int wis,
    int agi
) {
}
