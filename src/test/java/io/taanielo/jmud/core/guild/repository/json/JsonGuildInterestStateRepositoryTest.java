package io.taanielo.jmud.core.guild.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link JsonGuildInterestStateRepository} covering write-behind persistence round-trips,
 * absent-file defaults, and defensive loading of malformed or future-schema stores (issue #800).
 */
class JsonGuildInterestStateRepositoryTest {

    private static final Path FILE = Path.of("world-state", "guild-interest-state.json");

    @Test
    void savesAndReloadsCounterRoundTrip(@TempDir Path dataRoot) {
        JsonGuildInterestStateRepository repository = new JsonGuildInterestStateRepository(dataRoot);
        try {
            repository.saveGameDaysElapsed(863);
            waitForFile(dataRoot.resolve(FILE));
        } finally {
            repository.close();
        }

        JsonGuildInterestStateRepository reloaded = new JsonGuildInterestStateRepository(dataRoot);
        try {
            assertEquals(863, reloaded.loadGameDaysElapsed(),
                "The accrual counter must survive a restart, not reset to zero");
        } finally {
            reloaded.close();
        }
    }

    @Test
    void missingStoreLoadsAsZero(@TempDir Path dataRoot) {
        JsonGuildInterestStateRepository repository = new JsonGuildInterestStateRepository(dataRoot);
        try {
            assertEquals(0, repository.loadGameDaysElapsed(),
                "A world with no persisted accrual yet must start from zero");
        } finally {
            repository.close();
        }
    }

    @Test
    void malformedStoreLoadsAsZero(@TempDir Path dataRoot) throws Exception {
        Path dir = Files.createDirectories(dataRoot.resolve("world-state"));
        Files.writeString(dir.resolve("guild-interest-state.json"), "{ not valid json ][");

        JsonGuildInterestStateRepository repository = new JsonGuildInterestStateRepository(dataRoot);
        try {
            assertEquals(0, repository.loadGameDaysElapsed(),
                "A corrupt store must forfeit at most one period of accrual, not throw");
        } finally {
            repository.close();
        }
    }

    @Test
    void unsupportedSchemaVersionLoadsAsZero(@TempDir Path dataRoot) throws Exception {
        Path dir = Files.createDirectories(dataRoot.resolve("world-state"));
        Files.writeString(dir.resolve("guild-interest-state.json"), """
            {
              "schema_version": 99,
              "game_days_elapsed": 500
            }
            """);

        JsonGuildInterestStateRepository repository = new JsonGuildInterestStateRepository(dataRoot);
        try {
            assertEquals(0, repository.loadGameDaysElapsed(),
                "A store written by a future schema must not be partially trusted");
        } finally {
            repository.close();
        }
    }

    private static void waitForFile(Path path) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(path)) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertEquals(true, Files.exists(path), "Timed out waiting for file " + path);
    }
}
