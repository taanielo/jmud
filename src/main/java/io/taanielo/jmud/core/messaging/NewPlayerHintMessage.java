package io.taanielo.jmud.core.messaging;

import java.io.IOException;

import lombok.Value;

import io.taanielo.jmud.core.output.TextStyler;

/**
 * A short, one-time onboarding hint shown to a brand-new character immediately after character
 * creation completes (issue #517). It points the player at the tools that keep them alive in the
 * early game — the iron sword on the training-yard floor, the CONSIDER command, and the Master
 * Trainer — none of which are otherwise discoverable at level 1.
 *
 * <p>The text is intentionally static and self-contained: a new player must learn how to arm and
 * orient themselves without relying on any other channel (MOTD, docs, help files).
 */
@Value(staticConstructor = "of")
public class NewPlayerHintMessage implements Message {
    TextStyler textStyler;

    private static final String[] HINT_LINES = {
        "New here? A few tips to survive your first fights:",
        "  - Grab the iron sword on the floor: GET IRON SWORD, then WIELD IRON SWORD.",
        "  - CONSIDER a monster before you attack it to gauge the danger.",
        "  - Type TRAIN LIST at the Master Trainer to learn abilities.",
        "  - Type HELP at any time for the full list of commands."
    };

    @Override
    public void send(MessageWriter messageWriter) throws IOException {
        messageWriter.writeLine();
        messageWriter.writeLine(textStyler.title("--- Getting Started ---"));
        for (String line : HINT_LINES) {
            messageWriter.writeLine(textStyler.info(line));
        }
        messageWriter.writeLine();
    }
}
