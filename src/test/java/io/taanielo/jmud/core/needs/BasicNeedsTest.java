package io.taanielo.jmud.core.needs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

class BasicNeedsTest {

    @Test
    void decayEmitsEventsAndDamage() {
        NeedState hunger = NeedState.of(2, 10, 1, 4, 2);
        NeedState thirst = NeedState.of(5, 10, 1, 4, 2);
        BasicNeeds needs = BasicNeeds.of(Map.of(
            NeedId.HUNGER, hunger,
            NeedId.THIRST, thirst
        ));

        NeedsTickResult result = needs.decay();

        assertEquals(2, result.events().size());
        assertEquals(NeedsSettings.severeDamage(), result.damage());
        assertFalse(result.events().isEmpty());
    }
}
