package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.AuthenticationLimiter;
import io.taanielo.jmud.core.authentication.AuthenticationPolicy;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageWriter;

/**
 * Socket authentication for a single user
 *
 * Authentication process will wait until user has entered username. If username is existing user, password is asked
 * and verified. After successful authentication, success handler is invoked and authentication process is completed.
 */
@Slf4j
public class SocketAuthenticationService implements AuthenticationService {

    private final Socket clientSocket;
    private final UserRegistry userRegistry;
    private final MessageWriter messageWriter;
    private final AuthenticationPolicy policy;
    private final AuthenticationLimiter limiter;
    private final String remoteAddress;

    private Username username;
    private User authenticationUser;
    private boolean creatingUser;

    public SocketAuthenticationService(
        Socket clientSocket,
        UserRegistry userRegistry,
        MessageWriter messageWriter,
        AuthenticationPolicy policy,
        AuthenticationLimiter limiter
    ) {
        this.clientSocket = Objects.requireNonNull(clientSocket, "Socket is required");
        this.userRegistry = Objects.requireNonNull(userRegistry, "User registry is required");
        this.messageWriter = Objects.requireNonNull(messageWriter, "Message writer is required");
        this.policy = Objects.requireNonNull(policy, "Authentication policy is required");
        this.limiter = Objects.requireNonNull(limiter, "Authentication limiter is required");
        var address = clientSocket.getRemoteSocketAddress();
        this.remoteAddress = address == null ? "unknown" : address.toString();
    }

    @Override
    public void authenticate(String input, SuccessHandler successHandler) throws IOException {
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

    private void matchPassword(String input, SuccessHandler successHandler) throws IOException {
        log.debug("Password received");
        if (isBlocked(username.getValue())) {
            return;
        }
        if (authenticationUser.getPassword().matches(input)) {
            log.debug("Login successful");
            messageWriter.writeLine();
            messageWriter.writeLine("Login successful!");
            SocketCommand.enableEcho(clientSocket.getOutputStream());
            limiter.recordSuccess(attemptKey(username.getValue()));
            successHandler.handle(authenticationUser);
        } else {
            log.debug("Password doesn't match, login unsuccessful");
            limiter.recordFailure(attemptKey(username.getValue()));
            username = null;
            creatingUser = false;
            messageWriter.writeLine();
            messageWriter.writeLine("Incorrect password!");
            messageWriter.write("Enter username: ");
            SocketCommand.enableEcho(clientSocket.getOutputStream());
        }
    }

    private void findUser(String input) throws IOException {
        log.debug("Start authentication ..");
        log.debug("Username received");
        username = Username.of(input);
        Optional<User> existingUser = userRegistry.findByUsername(username);
        if (existingUser.isPresent()) {
            authenticationUser = existingUser.get();
            creatingUser = false;
            log.debug("User exists: {}", authenticationUser.getUsername().getValue());
            SocketCommand.disableEcho(clientSocket.getOutputStream());
            messageWriter.write("Enter password: ");
        } else {
            log.debug("User not found");
            if (!policy.allowNewUsers()) {
                username = null;
                creatingUser = false;
                messageWriter.writeLine("User not found. New user creation is disabled.");
                messageWriter.write("Enter username: ");
                return;
            }
            creatingUser = true;
            messageWriter.writeLine("User not found. Creating new user.");
            SocketCommand.disableEcho(clientSocket.getOutputStream());
            messageWriter.write("Enter password: ");
        }
    }

    private void createUser(String input, SuccessHandler successHandler) throws IOException {
        log.debug("Creating new user");
        Password password = Password.hash(input, policy.pbkdf2Iterations());
        authenticationUser = User.of(username, password);
        userRegistry.register(authenticationUser);
        creatingUser = false;
        messageWriter.writeLine();
        messageWriter.writeLine("Login successful!");
        SocketCommand.enableEcho(clientSocket.getOutputStream());
        limiter.recordSuccess(attemptKey(username.getValue()));
        successHandler.handle(authenticationUser);
    }

    private boolean isBlocked(String name) throws IOException {
        AuthenticationLimiter.AuthenticationLimitStatus status = limiter.check(attemptKey(name));
        if (status.allowed()) {
            return false;
        }
        Duration retryAfter = status.retryAfter();
        long seconds = Math.max(1, retryAfter.getSeconds());
        messageWriter.writeLine();
        messageWriter.writeLine("Too many login attempts. Try again in " + seconds + "s.");
        messageWriter.write("Enter username: ");
        SocketCommand.enableEcho(clientSocket.getOutputStream());
        username = null;
        creatingUser = false;
        return true;
    }

    private String attemptKey(String name) {
        String trimmed = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return remoteAddress + ":" + trimmed;
    }
}
