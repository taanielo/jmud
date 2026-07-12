package io.taanielo.jmud.core.character.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * JSON DTO for a class's per-level attribute gain schedule. Each field is an optional cadence string
 * ({@code "every_level"}, {@code "every_2_levels"}, {@code "every_3_levels"}); an omitted or
 * unrecognised value means the attribute does not grow from levelling. The {@code int} key is mapped
 * to the {@code intellect} component because {@code int} is a reserved Java word.
 *
 * @param str strength growth cadence
 * @param intellect intellect growth cadence (JSON key {@code int})
 * @param wis wisdom growth cadence
 * @param agi agility growth cadence
 */
public record AttributeGainsDto(
    @Nullable String str,
    @JsonProperty("int") @Nullable String intellect,
    @Nullable String wis,
    @Nullable String agi
) {
}
