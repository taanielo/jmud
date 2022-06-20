package io.taanielo.jmud.core.messaging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import lombok.Value;

@Value(staticConstructor = "of")
public class WelcomeMessage implements Message {

    @Override
    public void send(OutputStream outputStream) throws IOException {
        outputStream.write("\n\nWelcome to Xolo MUD!\n\nPlease enter your name: ".getBytes(StandardCharsets.UTF_8));
    }
}
