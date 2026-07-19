package io.taanielo.jmud.core.world.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Data transfer object for a room's optional standing environmental hazard (room schema v10).
 *
 * @param damageType    the resistible element the hazard deals ({@code FIRE}/{@code COLD}/{@code POISON})
 * @param damageMin     the inclusive minimum raw damage per tick
 * @param damageMax     the inclusive maximum raw damage per tick
 * @param damageMessage the player-facing line delivered to a victim each time the hazard bites
 */
public record RoomHazardDto(
    @JsonProperty("damage_type") @Nullable String damageType,
    @JsonProperty("damage_min") @Nullable Integer damageMin,
    @JsonProperty("damage_max") @Nullable Integer damageMax,
    @JsonProperty("damage_message") @Nullable String damageMessage
) {
}
