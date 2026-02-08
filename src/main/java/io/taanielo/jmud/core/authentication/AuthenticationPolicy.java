package io.taanielo.jmud.core.authentication;

import java.time.Duration;
import java.util.Objects;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Immutable authentication policy derived from configuration.
 */
public final class AuthenticationPolicy {
    private final boolean allowNewUsers;
    private final int maxAttempts;
    private final Duration attemptWindow;
    private final Duration lockoutDuration;
    private final int pbkdf2Iterations;

    private AuthenticationPolicy(
        boolean allowNewUsers,
        int maxAttempts,
        Duration attemptWindow,
        Duration lockoutDuration,
        int pbkdf2Iterations
    ) {
        this.allowNewUsers = allowNewUsers;
        this.maxAttempts = maxAttempts;
        this.attemptWindow = attemptWindow;
        this.lockoutDuration = lockoutDuration;
        this.pbkdf2Iterations = pbkdf2Iterations;
    }

    /**
     * Builds a policy from configuration, applying defaults when missing.
     */
    public static AuthenticationPolicy fromConfig(GameConfig config) {
        Objects.requireNonNull(config, "Config is required");
        boolean allowNewUsers = config.getBoolean("jmud.auth.allow_new_users", true);
        int maxAttempts = config.getInt("jmud.auth.max_attempts", 5);
        int windowSeconds = config.getInt("jmud.auth.attempt_window_seconds", 300);
        int lockoutSeconds = config.getInt("jmud.auth.lockout_seconds", 300);
        int iterations = config.getInt("jmud.auth.pbkdf2.iterations", Password.defaultIterations());
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("jmud.auth.max_attempts must be positive");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("jmud.auth.attempt_window_seconds must be positive");
        }
        if (lockoutSeconds <= 0) {
            throw new IllegalArgumentException("jmud.auth.lockout_seconds must be positive");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("jmud.auth.pbkdf2.iterations must be positive");
        }
        return new AuthenticationPolicy(
            allowNewUsers,
            maxAttempts,
            Duration.ofSeconds(windowSeconds),
            Duration.ofSeconds(lockoutSeconds),
            iterations
        );
    }

    /**
     * Returns whether new user creation is permitted.
     */
    public boolean allowNewUsers() {
        return allowNewUsers;
    }

    /**
     * Returns the maximum attempts allowed within the attempt window.
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Returns the attempt window used for rate limiting.
     */
    public Duration attemptWindow() {
        return attemptWindow;
    }

    /**
     * Returns the lockout duration after too many failed attempts.
     */
    public Duration lockoutDuration() {
        return lockoutDuration;
    }

    /**
     * Returns the PBKDF2 iteration count for new passwords.
     */
    public int pbkdf2Iterations() {
        return pbkdf2Iterations;
    }
}
