package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageWriter;

class SocketAuthenticationServiceTest {

    @Test
    void createsUserOnMissingUsername() throws IOException {
        RecordingUserRegistry registry = new RecordingUserRegistry();
        RecordingWriter writer = new RecordingWriter();
        SocketAuthenticationService service = new SocketAuthenticationService(new DummySocket(), registry, writer);

        service.authenticate("newplayer", user -> {
        });
        service.authenticate("pw123", user -> {
        });

        assertEquals(1, registry.users.size());
        assertEquals("newplayer", registry.users.get(0).getUsername().getValue());
        assertTrue(writer.lines.stream().anyMatch(line -> line.contains("Login successful")));
    }

    private static class RecordingUserRegistry implements UserRegistry {
        private final List<User> users = new ArrayList<>();

        @Override
        public Optional<User> findByUsername(Username username) {
            return users.stream().filter(user -> user.getUsername().equals(username)).findFirst();
        }

        @Override
        public void register(User user) {
            users.add(user);
        }
    }

    private static class RecordingWriter implements MessageWriter {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void write(String message) throws IOException {
            lines.add(message);
        }

        @Override
        public void writeLine() throws IOException {
            lines.add("");
        }

        @Override
        public void writeLine(String message) throws IOException {
            lines.add(message);
        }
    }

    private static class DummySocket extends Socket {
        private final OutputStream outputStream = new ByteArrayOutputStream();

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }
    }
}
