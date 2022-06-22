package io.taanielo.jmud.core.messaging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import lombok.Value;

import io.taanielo.jmud.core.authentication.Username;

@Value(staticConstructor = "of")
public class SimpleMessage implements Message {

    String value;
    Username username;

    @Override
    public void send(OutputStream outputStream) throws IOException {
        outputStream.write((username.getValue() + " said \"" + value + "\"\n").getBytes(StandardCharsets.UTF_8));
    }
}
