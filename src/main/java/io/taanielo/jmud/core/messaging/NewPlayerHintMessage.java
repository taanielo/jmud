package io.taanielo.jmud.core.messaging;

import java.io.IOException;
import java.util.List;

import lombok.Value;

import io.taanielo.jmud.core.output.TextStyler;

/**
 * A short, one-time onboarding hint shown to a brand-new character immediately after character
 * creation completes (issues #517, #782). It points the player at the tools that keep them alive in
 * the early game — {@code CONSIDER} to gauge a mob, {@code EQUIP}/{@code WIELD} to arm themselves,
 * {@code FLEE} to escape a losing fight, {@code TRAIN} to learn abilities — none of which are
 * otherwise discoverable at level 1. It also gives the brand-new character a concrete first goal
 * (issue #518): the Guild Clerk one room east in the Courtyard, whose easiest contract (the Rat
 * Catcher) is the intended level-1 starter quest.
 *
 * <p>The wording is data-driven ({@code data/new-player-hints.json}) so content authors can iterate
 * without code changes (AGENTS.md §11); this message is a thin renderer that takes the resolved
 * title and lines and writes them to the player's connection.
 */
@Value(staticConstructor = "of")
public class NewPlayerHintMessage implements Message {
    TextStyler textStyler;
    String title;
    List<String> lines;

    @Override
    public void send(MessageWriter messageWriter) throws IOException {
        if (lines.isEmpty()) {
            return;
        }
        messageWriter.writeLine();
        messageWriter.writeLine(textStyler.title("--- " + title + " ---"));
        for (String line : lines) {
            messageWriter.writeLine(textStyler.info(line));
        }
        messageWriter.writeLine();
    }
}
