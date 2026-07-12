package io.taanielo.jmud.core.server.websocket;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.AuthenticationLimiter;
import io.taanielo.jmud.core.authentication.AuthenticationPolicy;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.server.connection.ClientConnection;

/**
 * In-band username/password login for a single WebSocket connection. Behaviourally identical to
 * {@link io.taanielo.jmud.core.server.socket.SocketAuthenticationService} — same username lookup,
 * new-user creation, password verification and rate limiting — but it writes prompts through the
 * transport-neutral {@link ClientConnection} instead of a raw telnet socket, so no IAC echo-control
 * bytes ever reach the WebSocket wire (issue #526 §5). Password masking is the browser client's
 * responsibility.
 */
@Slf4j
public class WsAuthenticationService implements AuthenticationService {

    private final ClientConnection connection;
    private final UserRegistry userRegistry;
    private final AuthenticationPolicy policy;
    private final AuthenticationLimiter limiter;
    private final String remoteAddress;

    private @Nullable Username username;
    private @Nullable User authenticationUser;
    private boolean creatingUser;

    public WsAuthenticationService(
        ClientConnection connection,
        UserRegistry userRegistry,
        AuthenticationPolicy policy,
        AuthenticationLimiter limiter,
        String remoteAddress
    ) {
        this.connection = Objects.requireNonNull(connection, "Connection is required");
        this.userRegistry = Objects.requireNonNull(userRegistry, "User registry is required");
        this.policy = Objects.requireNonNull(policy, "Authentication policy is required");
        this.limiter = Objects.requireNonNull(limiter, "Authentication limiter is required");
        this.remoteAddress = Objects.requireNonNull(remoteAddress, "Remote address is required");
    }

    @Override
    public void authenticate(String input, SuccessHandler successHandler) {
        if (username == null) {
            if (isBlocked(input)) {
                return;
            }
            findUser(input);
        } else if (creatingUser) {
            if (isBlocked(username.getValue())) {
                return;
            }
            createUser(input, successHandler);
        } else {
            matchPassword(input, successHandler);
        }
    }

    private void matchPassword(String input, SuccessHandler successHandler) {
        Username currentUsername = Objects.requireNonNull(username, "Username expected during password match");
        User currentUser = Objects.requireNonNull(authenticationUser, "User expected during password match");
        if (isBlocked(currentUsername.getValue())) {
            return;
        }
        if (currentUser.getPassword().matches(input)) {
            log.debug("Login successful");
            connection.writeLine("");
            connection.writeLine("Login successful!");
            limiter.recordSuccess(attemptKey(currentUsername.getValue()));
            successHandler.handle(currentUser);
        } else {
            log.debug("Password doesn't match, login unsuccessful");
            limiter.recordFailure(attemptKey(currentUsername.getValue()));
            username = null;
            creatingUser = false;
            connection.writeLine("");
            connection.writeLine("Incorrect password!");
            connection.write("Enter username: ");
        }
    }

    private void findUser(String input) {
        log.debug("Username received");
        Username enteredUsername = Username.of(input);
        this.username = enteredUsername;
        Optional<User> existingUser = userRegistry.findByUsername(enteredUsername);
        if (existingUser.isPresent()) {
            authenticationUser = existingUser.get();
            creatingUser = false;
            connection.write("Enter password: ");
        } else {
            if (!policy.allowNewUsers()) {
                username = null;
                creatingUser = false;
                connection.writeLine("User not found. New user creation is disabled.");
                connection.write("Enter username: ");
                return;
            }
            creatingUser = true;
            connection.writeLine("User not found. Creating new user.");
            connection.write("Enter password: ");
        }
    }

    private void createUser(String input, SuccessHandler successHandler) {
        Username currentUsername = Objects.requireNonNull(username, "Username expected during user creation");
        Password password = Password.hash(input, policy.pbkdf2Iterations());
        User createdUser = User.of(currentUsername, password);
        authenticationUser = createdUser;
        userRegistry.register(createdUser);
        creatingUser = false;
        connection.writeLine("");
        connection.writeLine("Login successful!");
        limiter.recordSuccess(attemptKey(currentUsername.getValue()));
        successHandler.handle(createdUser);
    }

    private boolean isBlocked(String name) {
        AuthenticationLimiter.AuthenticationLimitStatus status = limiter.check(attemptKey(name));
        if (status.allowed()) {
            return false;
        }
        Duration retryAfter = status.retryAfter();
        long seconds = Math.max(1, retryAfter.toSeconds());
        connection.writeLine("");
        connection.writeLine("Too many login attempts. Try again in " + seconds + "s.");
        connection.write("Enter username: ");
        username = null;
        creatingUser = false;
        return true;
    }

    private String attemptKey(@Nullable String name) {
        String trimmed = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return remoteAddress + ":" + trimmed;
    }
}
