package io.taanielo.jmud.core.server.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.server.connection.ClientConnection;

class WsAuthenticationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsUserOnMissingUsername() throws IOException {
        RecordingUserRegistry registry = new RecordingUserRegistry();
        RecordingConnection connection = new RecordingConnection();
        AuthenticationPolicy policy = createPolicy();
        AuthenticationLimiter limiter = new AuthenticationLimiter(policy, Clock.systemUTC());
        List<User> loggedIn = new ArrayList<>();
        WsAuthenticationService service =
            new WsAuthenticationService(connection, registry, policy, limiter, "127.0.0.1:5555");

        service.authenticate("newplayer", loggedIn::add);
        service.authenticate("pw123", loggedIn::add);

        assertEquals(1, registry.users.size());
        assertEquals("newplayer", registry.users.get(0).getUsername().getValue());
        assertEquals(1, loggedIn.size());
        assertTrue(connection.output.stream().anyMatch(line -> line.contains("Login successful")));
    }

    @Test
    void doesNotCreateUserAfterBadPassword() throws IOException {
        RecordingUserRegistry registry = new RecordingUserRegistry();
        registry.register(User.of(Username.of("sparky"), Password.hash("qwerty", 1000)));
        RecordingConnection connection = new RecordingConnection();
        AuthenticationPolicy policy = createPolicy();
        AuthenticationLimiter limiter = new AuthenticationLimiter(policy, Clock.systemUTC());
        WsAuthenticationService service =
            new WsAuthenticationService(connection, registry, policy, limiter, "127.0.0.1:5555");

        service.authenticate("sparky", user -> {
        });
        service.authenticate("wrong", user -> {
        });

        assertTrue(connection.output.stream().anyMatch(line -> line.contains("Incorrect password")));
        assertTrue(connection.output.stream().noneMatch(line -> line.contains("Creating new user")));
    }

    @Test
    void emitsNoTelnetControlBytesDuringLogin() throws IOException {
        RecordingUserRegistry registry = new RecordingUserRegistry();
        RecordingConnection connection = new RecordingConnection();
        AuthenticationPolicy policy = createPolicy();
        AuthenticationLimiter limiter = new AuthenticationLimiter(policy, Clock.systemUTC());
        WsAuthenticationService service =
            new WsAuthenticationService(connection, registry, policy, limiter, "127.0.0.1:5555");

        service.authenticate("brandnew", user -> {
        });
        service.authenticate("secret", user -> {
        });

        String allOutput = String.join("", connection.output);
        assertFalse(allOutput.chars().anyMatch(c -> c == 0xFF || c == 0xFB || c == 0xFC),
            "WebSocket login must not emit telnet IAC echo-control bytes");
    }

    private AuthenticationPolicy createPolicy() throws IOException {
        Path configPath = tempDir.resolve("auth.properties");
        Files.writeString(configPath, """
                                      jmud.auth.allow_new_users=true
                                      jmud.auth.max_attempts=5
                                      jmud.auth.attempt_window_seconds=300
                                      jmud.auth.lockout_seconds=300
                                      jmud.auth.pbkdf2.iterations=1000\
                                      """);
        return AuthenticationPolicy.fromConfig(GameConfig.load(configPath));
    }

    private static final class RecordingUserRegistry implements UserRegistry {
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

    private static final class RecordingConnection implements ClientConnection {
        private final List<String> output = new ArrayList<>();

        @Override
        public void open() {
        }

        @Override
        public String readLine() {
            return null;
        }

        @Override
        public MessageWriter messageWriter() {
            return output::add;
        }

        @Override
        public void sendMessage(Message message) {
        }

        @Override
        public void writeLine(String message) {
            output.add(message);
        }

        @Override
        public void writeLines(List<String> lines) {
            output.addAll(lines);
        }

        @Override
        public void write(String text) {
            output.add(text);
        }

        @Override
        public void close() {
        }
    }
}
