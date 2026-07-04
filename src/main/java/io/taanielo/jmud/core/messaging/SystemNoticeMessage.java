package io.taanielo.jmud.core.messaging;

import java.io.IOException;
import java.util.Objects;

/**
 * Plain-text operational announcement (e.g. an impending server shutdown),
 * sent with no ANSI styling so it renders identically for every client.
 */
public record SystemNoticeMessage(String text) implements Message {

    public SystemNoticeMessage {
        Objects.requireNonNull(text, "Text is required");
    }

    @Override
    public void send(MessageWriter messageWriter) throws IOException {
        messageWriter.writeLine();
        messageWriter.writeLine(text);
    }
}
