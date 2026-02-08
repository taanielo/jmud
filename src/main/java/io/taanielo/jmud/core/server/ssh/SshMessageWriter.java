package io.taanielo.jmud.core.server.ssh;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.taanielo.jmud.core.messaging.MessageWriter;

/**
 * Message writer for SSH streams.
 */
public class SshMessageWriter implements MessageWriter {

    private final OutputStream output;

    public SshMessageWriter(OutputStream output) {
        this.output = Objects.requireNonNull(output, "Output stream is required");
    }

    @Override
    public void write(String message) throws IOException {
        output.write(message.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
