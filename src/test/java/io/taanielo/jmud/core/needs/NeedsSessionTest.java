package io.taanielo.jmud.core.needs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;

class NeedsSessionTest {

    @Test
    void emitsWarningAndDamageMessages() {
        NeedState hunger = NeedState.of(3, 10, 1, 4, 2);
        NeedState thirst = NeedState.of(2, 10, 1, 4, 2);
        BasicNeeds needs = BasicNeeds.of(Map.of(
            NeedId.HUNGER, hunger,
            NeedId.THIRST, thirst
        ));
        NeedsSession session = NeedsSession.withNeeds(Username.of("Bob"), needs, 10);

        NeedsTickOutcome outcome = session.tick();

        assertTrue(outcome.messages().stream().anyMatch(message -> message.contains("hungry")));
        assertTrue(outcome.messages().stream().anyMatch(message -> message.contains("damage")));
        assertEquals(9, outcome.session().health());
    }
}
