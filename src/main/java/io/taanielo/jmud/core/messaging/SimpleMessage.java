package io.taanielo.jmud.core.messaging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import lombok.Value;

@Value(staticConstructor = "of")
public class SimpleMessage implements Message {

    String value;
    String playerName;

    @Override
    public void send(OutputStream outputStream) throws IOException {
        outputStream.write((playerName + " said \"" + value + "\"\n").getBytes(StandardCharsets.UTF_8));
    }
}
