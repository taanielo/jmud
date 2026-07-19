package io.taanielo.jmud.core.effects.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.effects.ControlType;
import io.taanielo.jmud.core.effects.EffectDefinition;

/**
 * Unit tests for {@link EffectDefinitionMapper}, focused on the additive {@code control}
 * classification introduced for crowd-control effects.
 */
class EffectDefinitionMapperTest {

    private final EffectDefinitionMapper mapper = new EffectDefinitionMapper();

    @Test
    void mapsControlClassificationCaseInsensitively() {
        EffectDefinition definition = mapper.toDomain(dto("ROOT"));

        assertEquals(ControlType.ROOT, definition.control().orElseThrow());
    }

    @Test
    void absentControlLeavesEffectUnclassified() {
        EffectDefinition definition = mapper.toDomain(dto(null));

        assertTrue(definition.control().isEmpty());
    }

    @Test
    void blankControlLeavesEffectUnclassified() {
        EffectDefinition definition = mapper.toDomain(dto("  "));

        assertTrue(definition.control().isEmpty());
    }

    @Test
    void unknownControlValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(dto("charm")));
    }

    private static EffectDefinitionDto dto(String control) {
        return new EffectDefinitionDto(
            2,
            "rooted",
            "Rooted",
            6,
            1,
            "refresh",
            control,
            List.of(),
            List.of()
        );
    }
}
