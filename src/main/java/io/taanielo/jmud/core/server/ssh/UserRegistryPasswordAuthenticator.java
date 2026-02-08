package io.taanielo.jmud.core.server.ssh;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import io.taanielo.jmud.core.authentication.AuthenticationLimiter;
import io.taanielo.jmud.core.authentication.AuthenticationPolicy;
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
    private final AuthenticationPolicy policy;
    private final AuthenticationLimiter limiter;

    public UserRegistryPasswordAuthenticator(
        UserRegistry userRegistry,
        AuthenticationPolicy policy,
        AuthenticationLimiter limiter
    ) {
        this.userRegistry = Objects.requireNonNull(userRegistry, "User registry is required");
        this.policy = Objects.requireNonNull(policy, "Authentication policy is required");
        this.limiter = Objects.requireNonNull(limiter, "Authentication limiter is required");
    }

    @Override
    public boolean authenticate(String username, String password, ServerSession session) {
        String key = attemptKey(session, username);
        AuthenticationLimiter.AuthenticationLimitStatus status = limiter.check(key);
        if (!status.allowed()) {
            log.warn("SSH authentication rate limited for {}", username);
            return false;
        }
        Username userId = Username.of(username);
        Optional<User> existingUser = userRegistry.findByUsername(userId);
        if (existingUser.isPresent()) {
            boolean matches = existingUser.get().getPassword().matches(password);
            if (!matches) {
                limiter.recordFailure(key);
                log.debug("SSH authentication failed for user {}", username);
                return false;
            }
            limiter.recordSuccess(key);
            session.setAttribute(SshSessionAttributes.AUTHENTICATED_USER, existingUser.get());
            session.setAttribute(SshSessionAttributes.NEW_USER, false);
            return true;
        }
        if (!policy.allowNewUsers()) {
            log.warn("SSH new user creation denied for {}", username);
            return false;
        }
        User newUser = User.of(userId, Password.hash(password, policy.pbkdf2Iterations()));
        userRegistry.register(newUser);
        limiter.recordSuccess(key);
        session.setAttribute(SshSessionAttributes.AUTHENTICATED_USER, newUser);
        session.setAttribute(SshSessionAttributes.NEW_USER, true);
        log.info("SSH user created: {}", username);
        return true;
    }

    private String attemptKey(ServerSession session, String username) {
        String address = session.getClientAddress() == null ? "unknown" : session.getClientAddress().toString();
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return address + ":" + normalized;
    }
}
