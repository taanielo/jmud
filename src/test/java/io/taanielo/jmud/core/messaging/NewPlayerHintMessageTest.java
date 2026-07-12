package io.taanielo.jmud.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(output)
            .contains("Getting Started")
            .contains("GET IRON SWORD")
            .contains("WIELD IRON SWORD")
            .contains("CONSIDER")
            .contains("TRAIN LIST")
            .contains("HELP");
    }

    @Test
    void mentionsEachTipExactlyOnce() throws IOException {
        NewPlayerHintMessage.of(new PlainTextStyler()).send(writer);

        String output = String.join("", written);
        assertThat(countOccurrences(output, "WIELD IRON SWORD")).isEqualTo(1);
        assertThat(countOccurrences(output, "TRAIN LIST")).isEqualTo(1);
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
