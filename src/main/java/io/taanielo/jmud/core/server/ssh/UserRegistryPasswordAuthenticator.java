package io.taanielo.jmud.core.server.ssh;

import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.Username;

/**
 * Password authenticator backed by the game user registry.
 */
@Slf4j
public class UserRegistryPasswordAuthenticator implements PasswordAuthenticator {

    private final UserRegistry userRegistry;

    public UserRegistryPasswordAuthenticator(UserRegistry userRegistry) {
        this.userRegistry = Objects.requireNonNull(userRegistry, "User registry is required");
    }

    @Override
    public boolean authenticate(String username, String password, ServerSession session) {
        Username userId = Username.of(username);
        Optional<User> existingUser = userRegistry.findByUsername(userId);
        if (existingUser.isPresent()) {
            boolean matches = existingUser.get().getPassword().equals(Password.of(password));
            if (!matches) {
                log.debug("SSH authentication failed for user {}", username);
                return false;
            }
            session.setAttribute(SshSessionAttributes.AUTHENTICATED_USER, existingUser.get());
            session.setAttribute(SshSessionAttributes.NEW_USER, false);
            return true;
        }
        User newUser = User.of(userId, Password.of(password));
        userRegistry.register(newUser);
        session.setAttribute(SshSessionAttributes.AUTHENTICATED_USER, newUser);
        session.setAttribute(SshSessionAttributes.NEW_USER, true);
        log.info("SSH user created: {}", username);
        return true;
    }
}
