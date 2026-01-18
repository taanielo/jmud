package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.Socket;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

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

    private Username username;
    private User authenticationUser;
    private boolean creatingUser;

    public SocketAuthenticationService(Socket clientSocket, UserRegistry userRegistry, MessageWriter messageWriter) {
        this.clientSocket = clientSocket;
        this.userRegistry = userRegistry;
        this.messageWriter = messageWriter;
    }

    @Override
    public void authenticate(String input, SuccessHandler successHandler) throws IOException {
        if (username == null) {
            findUser(input);
        } else if (creatingUser) {
            createUser(input, successHandler);
        } else {
            matchPassword(input, successHandler);
        }
    }

    private void matchPassword(String input, SuccessHandler successHandler) throws IOException {
        log.debug("Password received");
        Password password = Password.of(input);
        if (authenticationUser.getPassword().equals(password)) {
            log.debug("Login successful");
            messageWriter.writeLine();
            messageWriter.writeLine("Login successful!");
            SocketCommand.enableEcho(clientSocket.getOutputStream());
            successHandler.handle(authenticationUser);
        } else {
            log.debug("Password doesn't match, login unsuccessful");
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
            creatingUser = true;
            messageWriter.writeLine("User not found. Creating new user.");
            SocketCommand.disableEcho(clientSocket.getOutputStream());
            messageWriter.write("Enter password: ");
        }
    }

    private void createUser(String input, SuccessHandler successHandler) throws IOException {
        log.debug("Creating new user");
        Password password = Password.of(input);
        authenticationUser = User.of(username, password);
        userRegistry.register(authenticationUser);
        creatingUser = false;
        messageWriter.writeLine();
        messageWriter.writeLine("Login successful!");
        SocketCommand.enableEcho(clientSocket.getOutputStream());
        successHandler.handle(authenticationUser);
    }
}
