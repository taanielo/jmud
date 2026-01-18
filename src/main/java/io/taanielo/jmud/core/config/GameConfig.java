package io.taanielo.jmud.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

public class GameConfig {
    private static final String DEFAULT_RESOURCE = "jmud.properties";

    private final Properties properties;

    private GameConfig(Properties properties) {
        this.properties = properties;
    }

    public static GameConfig load() {
        return load(DEFAULT_RESOURCE);
    }

    public static GameConfig load(String resourceName) {
        Properties properties = new Properties();
        try (InputStream input = GameConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config resource " + resourceName, e);
        }
        return new GameConfig(properties);
    }

    public static GameConfig load(Path path) {
        Properties properties = new Properties();
        try (InputStream input = java.nio.file.Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config file " + path, e);
        }
        return new GameConfig(properties);
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public int getInt(String key, int fallback) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + key + ": " + value, e);
        }
    }

    public long getLong(String key, long fallback) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long for " + key + ": " + value, e);
        }
    }

    public String getString(String key, String fallback) {
        String value = getValue(key);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String getValue(String key) {
        Objects.requireNonNull(key, "Config key is required");
        String override = System.getProperty(key);
        if (override != null) {
            return override;
        }
        return properties.getProperty(key);
    }
}
