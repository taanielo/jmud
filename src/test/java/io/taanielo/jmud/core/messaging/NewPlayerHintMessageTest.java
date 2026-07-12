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

    @Test
    void includesTheEarlyGameSurvivalTips() throws IOException {
        NewPlayerHintMessage.of(new PlainTextStyler()).send(writer);

        String output = String.join("", written);
        assertContains(output, "Getting Started");
        assertContains(output, "GET IRON SWORD");
        assertContains(output, "WIELD IRON SWORD");
        assertContains(output, "CONSIDER");
        assertContains(output, "TRAIN LIST");
        assertContains(output, "HELP");
    }

    @Test
    void mentionsEachTipExactlyOnce() throws IOException {
        NewPlayerHintMessage.of(new PlainTextStyler()).send(writer);

        String output = String.join("", written);
        assertEquals(1, countOccurrences(output, "WIELD IRON SWORD"));
        assertEquals(1, countOccurrences(output, "TRAIN LIST"));
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
