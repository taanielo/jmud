package io.taanielo.jmud.core.settings;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ApplicationSettings {
    private static final String SETTINGS_FILE = "application.properties";
    private static final Properties PROPERTIES = load();

    private ApplicationSettings() {
    }

    public static String getString(String key, String fallback) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = ApplicationSettings.class.getClassLoader().getResourceAsStream(SETTINGS_FILE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + SETTINGS_FILE, e);
        }
        return properties;
    }
}
