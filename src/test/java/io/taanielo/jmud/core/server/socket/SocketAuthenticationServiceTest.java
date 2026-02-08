package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.authentication.AuthenticationLimiter;
import io.taanielo.jmud.core.authentication.AuthenticationPolicy;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.config.GameConfig;
import io.taanielo.jmud.core.messaging.MessageWriter;

class SocketAuthenticationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsUserOnMissingUsername() throws IOException {
        RecordingUserRegistry registry = new RecordingUserRegistry();
        RecordingWriter writer = new RecordingWriter();
        AuthenticationPolicy policy = createPolicy();
        AuthenticationLimiter limiter = new AuthenticationLimiter(policy, Clock.systemUTC());
        SocketAuthenticationService service = new SocketAuthenticationService(new DummySocket(), registry, writer, policy, limiter);

        service.authenticate("newplayer", user -> {
        });
        service.authenticate("pw123", user -> {
        });

        assertEquals(1, registry.users.size());
        assertEquals("newplayer", registry.users.get(0).getUsername().getValue());
        assertTrue(writer.lines.stream().anyMatch(line -> line.contains("Login successful")));
    }

    @Test
    void doesNotCreateUserAfterBadPassword() throws IOException {
        RecordingUserRegistry registry = new RecordingUserRegistry();
        registry.register(User.of(Username.of("sparky"), Password.hash("qwerty", 1000)));
        RecordingWriter writer = new RecordingWriter();
        AuthenticationPolicy policy = createPolicy();
        AuthenticationLimiter limiter = new AuthenticationLimiter(policy, Clock.systemUTC());
        SocketAuthenticationService service = new SocketAuthenticationService(new DummySocket(), registry, writer, policy, limiter);

        service.authenticate("sparky", user -> {
        });
        service.authenticate("wrong", user -> {
        });

        assertTrue(writer.lines.stream().anyMatch(line -> line.contains("Incorrect password")));
        assertTrue(writer.lines.stream().noneMatch(line -> line.contains("Creating new user")));
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

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }
    }

    private AuthenticationPolicy createPolicy() throws IOException {
        Path configPath = tempDir.resolve("auth.properties");
        Files.writeString(configPath, String.join("\n",
            "jmud.auth.allow_new_users=true",
            "jmud.auth.max_attempts=5",
            "jmud.auth.attempt_window_seconds=300",
            "jmud.auth.lockout_seconds=300",
            "jmud.auth.pbkdf2.iterations=1000"
        ));
        return AuthenticationPolicy.fromConfig(GameConfig.load(configPath));
    }
}
