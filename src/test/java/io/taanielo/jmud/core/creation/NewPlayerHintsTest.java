package io.taanielo.jmud.core.creation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class NewPlayerHintsTest {

    @Test
    void emptyHasNoLines() {
        assertFalse(NewPlayerHints.EMPTY.hasLines());
        assertTrue(NewPlayerHints.EMPTY.lines().isEmpty());
    }

    @Test
    void hasLinesReportsPresenceOfContent() {
        NewPlayerHints hints = new NewPlayerHints("Getting Started", List.of("one", "two"));

        assertTrue(hints.hasLines());
        assertEquals(2, hints.lines().size());
    }

    @Test
    void linesAreDefensivelyCopied() {
        List<String> source = new ArrayList<>(List.of("one"));
        NewPlayerHints hints = new NewPlayerHints("Getting Started", source);

        source.add("mutated");

        assertEquals(1, hints.lines().size(), "the record must snapshot the lines it was given");
    }

    @Test
    void requiresNonNullTitleAndLines() {
        assertThrows(NullPointerException.class, () -> new NewPlayerHints(null, List.of()));
        assertThrows(NullPointerException.class, () -> new NewPlayerHints("Getting Started", null));
    }
}
