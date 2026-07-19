package io.taanielo.jmud.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.output.PlainTextStyler;

class NewPlayerHintMessageTest {

    private final List<String> written = new ArrayList<>();
    private final MessageWriter writer = written::add;

    private static final List<String> HINTS = List.of(
        "Welcome to the realm! A few commands that will keep you alive:",
        "  CONSIDER <mob>  - size up an enemy before you attack it.",
        "  EQUIP <item>    - wield your weapon (WIELD works too).",
        "  FLEE            - escape a fight that is going badly.",
        "  TRAIN LIST      - learn abilities at the Master Trainer.",
        "  HELP            - the full list of commands.",
        "Your first job: go EAST to the Courtyard, then QUEST LIST at the Guild Clerk.");

    @Test
    void rendersTheProvidedTitleAndEachLine() throws IOException {
        NewPlayerHintMessage.of(new PlainTextStyler(), "Getting Started", HINTS).send(writer);

        String output = String.join("", written);
        assertContains(output, "Getting Started");
        assertContains(output, "CONSIDER");
        assertContains(output, "EQUIP");
        assertContains(output, "WIELD");
        assertContains(output, "FLEE");
        assertContains(output, "TRAIN LIST");
        assertContains(output, "HELP");
    }

    @Test
    void pointsNewPlayerAtTheGuildClerkForAFirstQuest() throws IOException {
        NewPlayerHintMessage.of(new PlainTextStyler(), "Getting Started", HINTS).send(writer);

        String output = String.join("", written);
        assertContains(output, "Courtyard");
        assertContains(output, "QUEST LIST");
        assertContains(output, "Guild Clerk");
    }

    @Test
    void rendersEachLineExactlyOnce() throws IOException {
        NewPlayerHintMessage.of(new PlainTextStyler(), "Getting Started", HINTS).send(writer);

        String output = String.join("", written);
        assertEquals(1, countOccurrences(output, "CONSIDER"));
        assertEquals(1, countOccurrences(output, "TRAIN LIST"));
    }

    @Test
    void writesNothingWhenThereAreNoLines() throws IOException {
        NewPlayerHintMessage.of(new PlainTextStyler(), "Getting Started", List.of()).send(writer);

        assertTrue(written.isEmpty(), "Expected no output when there are no hint lines");
    }

    private static void assertContains(String haystack, String needle) {
        assertTrue(haystack.contains(needle), () -> "Expected output to contain: " + needle);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
