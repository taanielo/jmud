package io.taanielo.jmud.core.messaging;

import java.io.IOException;
import java.util.Objects;

/**
 * A single line of unstyled text, e.g. chat/emote output routed through
 * {@link MessageBroadcaster}. Unlike {@link SystemNoticeMessage}, it does not
 * prepend a blank line, so it matches the plain {@code writeLine} behaviour
 * previously hand-rolled by socket adapters for say/tell/gossip/emote.
 */
public record PlainTextMessage(String text) implements Message {

    public PlainTextMessage {
        Objects.requireNonNull(text, "Text is required");
    }

    @Override
    public void send(MessageWriter messageWriter) throws IOException {
        messageWriter.writeLine(text);
    }
}
