package io.taanielo.jmud.core.authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.config.GameConfig;

class AuthenticationLimiterTest {

    @TempDir
    Path tempDir;

    @Test
    void blocksAfterMaxAttempts() throws IOException {
        Path configPath = tempDir.resolve("auth.properties");
        Files.writeString(configPath, String.join("\n",
            "jmud.auth.allow_new_users=true",
            "jmud.auth.max_attempts=2",
            "jmud.auth.attempt_window_seconds=10",
            "jmud.auth.lockout_seconds=30",
            "jmud.auth.pbkdf2.iterations=1000"
        ));
        AuthenticationPolicy policy = AuthenticationPolicy.fromConfig(GameConfig.load(configPath));
        MutableClock clock = new MutableClock(Instant.EPOCH);
        AuthenticationLimiter limiter = new AuthenticationLimiter(policy, clock);

        String key = "client:alice";
        assertTrue(limiter.check(key).allowed());
        limiter.recordFailure(key);
        assertTrue(limiter.check(key).allowed());
        limiter.recordFailure(key);
        assertFalse(limiter.check(key).allowed());

        clock.advance(Duration.ofSeconds(31));
        assertTrue(limiter.check(key).allowed());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
