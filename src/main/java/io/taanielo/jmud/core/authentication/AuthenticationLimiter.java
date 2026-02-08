package io.taanielo.jmud.core.authentication;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter for authentication attempts.
 */
public final class AuthenticationLimiter {
    private final AuthenticationPolicy policy;
    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public AuthenticationLimiter(AuthenticationPolicy policy, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "Policy is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    /**
     * Checks whether another authentication attempt is allowed.
     */
    public AuthenticationLimitStatus check(String key) {
        String normalized = normalizeKey(key);
        Instant now = clock.instant();
        AttemptState state = attempts.get(normalized);
        if (state == null) {
            return AuthenticationLimitStatus.allow();
        }
        if (state.isBlockedAt(now)) {
            Duration retryAfter = Duration.between(now, state.blockedUntil());
            return AuthenticationLimitStatus.block(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
        }
        if (state.isWindowExpired(now, policy.attemptWindow())) {
            attempts.remove(normalized, state);
        }
        return AuthenticationLimitStatus.allow();
    }

    /**
     * Records a failed authentication attempt.
     */
    public void recordFailure(String key) {
        String normalized = normalizeKey(key);
        Instant now = clock.instant();
        attempts.compute(normalized, (ignored, current) -> {
            AttemptState state = current;
            if (state == null || state.isWindowExpired(now, policy.attemptWindow()) || state.isBlockExpired(now)) {
                state = AttemptState.start(now);
            }
            int nextAttempts = state.attempts() + 1;
            Instant blockedUntil = nextAttempts >= policy.maxAttempts()
                ? now.plus(policy.lockoutDuration())
                : state.blockedUntil();
            return state.with(nextAttempts, blockedUntil);
        });
    }

    /**
     * Records a successful authentication attempt and clears limiter state.
     */
    public void recordSuccess(String key) {
        String normalized = normalizeKey(key);
        attempts.remove(normalized);
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return "unknown";
        }
        String trimmed = key.trim();
        return trimmed.isEmpty() ? "unknown" : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Result of a limiter check with optional retry delay.
     */
    public record AuthenticationLimitStatus(boolean allowed, Duration retryAfter) {
        /**
         * Creates an allowed status.
         */
        public static AuthenticationLimitStatus allow() {
            return new AuthenticationLimitStatus(true, Duration.ZERO);
        }

        /**
         * Creates a blocked status.
         */
        public static AuthenticationLimitStatus block(Duration retryAfter) {
            return new AuthenticationLimitStatus(false, retryAfter);
        }
    }

    private record AttemptState(int attempts, Instant windowStart, Instant blockedUntil) {
        static AttemptState start(Instant now) {
            return new AttemptState(0, now, null);
        }

        boolean isWindowExpired(Instant now, Duration window) {
            return windowStart.plus(window).isBefore(now);
        }

        boolean isBlockedAt(Instant now) {
            return blockedUntil != null && blockedUntil.isAfter(now);
        }

        boolean isBlockExpired(Instant now) {
            return blockedUntil != null && !blockedUntil.isAfter(now);
        }

        AttemptState with(int nextAttempts, Instant nextBlockedUntil) {
            return new AttemptState(nextAttempts, windowStart, nextBlockedUntil);
        }
    }
}
