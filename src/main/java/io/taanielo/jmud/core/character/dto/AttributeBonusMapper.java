package io.taanielo.jmud.core.character.dto;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.character.AttributeBonus;
import io.taanielo.jmud.core.character.AttributeGainCadence;
import io.taanielo.jmud.core.character.AttributeGainSchedule;

/**
 * Maps the optional attribute JSON blocks shared by race and class data to their domain value
 * objects, treating an absent block as no bonus / no growth.
 */
public final class AttributeBonusMapper {

    private AttributeBonusMapper() {
    }

    /**
     * Converts an optional {@link AttributeBonusDto} to an {@link AttributeBonus}, defaulting a
     * {@code null} block to {@link AttributeBonus#NONE}.
     *
     * @param dto the DTO, or {@code null} when the JSON omitted the block
     * @return the domain attribute bonus; never {@code null}
     */
    public static AttributeBonus toDomain(@Nullable AttributeBonusDto dto) {
        if (dto == null) {
            return AttributeBonus.NONE;
        }
        return new AttributeBonus(dto.str(), dto.intellect(), dto.wis(), dto.agi());
    }

    /**
     * Converts an optional {@link AttributeGainsDto} to an {@link AttributeGainSchedule}, defaulting a
     * {@code null} block to {@link AttributeGainSchedule#NONE}.
     *
     * @param dto the DTO, or {@code null} when the JSON omitted the block
     * @return the domain attribute gain schedule; never {@code null}
     */
    public static AttributeGainSchedule toDomain(@Nullable AttributeGainsDto dto) {
        if (dto == null) {
            return AttributeGainSchedule.NONE;
        }
        return new AttributeGainSchedule(
            AttributeGainCadence.fromString(dto.str()),
            AttributeGainCadence.fromString(dto.intellect()),
            AttributeGainCadence.fromString(dto.wis()),
            AttributeGainCadence.fromString(dto.agi())
        );
    }
}
