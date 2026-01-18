package io.taanielo.jmud.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsDefaultsFromResource() throws IOException {
        Path configFile = tempDir.resolve("jmud.properties");
        Files.writeString(configFile, "jmud.effects.enabled=false\n");

        GameConfig config = GameConfig.load(configFile);

        assertTrue(!config.getBoolean("jmud.effects.enabled", true));
    }

    @Test
    void fallsBackToSystemProperties() throws IOException {
        System.setProperty("jmud.tick.interval.ms", "777");
        try {
            GameConfig config = GameConfig.load();
            assertEquals(777, config.getLong("jmud.tick.interval.ms", 1));
        } finally {
            System.clearProperty("jmud.tick.interval.ms");
        }
    }
}
