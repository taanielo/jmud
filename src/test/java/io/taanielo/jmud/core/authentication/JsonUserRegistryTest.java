package io.taanielo.jmud.core.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonUserRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsUser() throws Exception {
        JsonUserRegistry registry = new JsonUserRegistry(tempDir);
        User user = User.of(Username.of("gandalf"), Password.hash("mellon", 1000));

        registry.register(user);

        Optional<User> loaded = registry.findByUsername(Username.of("gandalf"));
        assertTrue(loaded.isPresent());
        assertEquals("gandalf", loaded.get().getUsername().getValue());
        assertTrue(loaded.get().getPassword().matches("mellon"));
    }

    @Test
    void returnsEmptyForUnknownUser() throws Exception {
        JsonUserRegistry registry = new JsonUserRegistry(tempDir);

        Optional<User> result = registry.findByUsername(Username.of("nobody"));

        assertFalse(result.isPresent());
    }

    @Test
    void doesNotOverwriteExistingUser() throws Exception {
        JsonUserRegistry registry = new JsonUserRegistry(tempDir);
        User first = User.of(Username.of("bilbo"), Password.hash("shire", 1000));
        User second = User.of(Username.of("bilbo"), Password.hash("ring", 1000));

        registry.register(first);
        registry.register(second);

        Optional<User> loaded = registry.findByUsername(Username.of("bilbo"));
        assertTrue(loaded.isPresent());
        // First registration wins; password should still match "shire", not "ring"
        assertTrue(loaded.get().getPassword().matches("shire"));
        assertFalse(loaded.get().getPassword().matches("ring"));
    }

    @Test
    void loadsUserFromDiskAfterCacheIsCleared() throws Exception {
        Path dataRoot = tempDir.resolve("data");

        // First registry instance: register a user (writes to disk)
        JsonUserRegistry registryA = new JsonUserRegistry(dataRoot);
        User user = User.of(Username.of("frodo"), Password.hash("baggins", 1000));
        registryA.register(user);

        // Second registry instance: no warm cache, must read from disk
        JsonUserRegistry registryB = new JsonUserRegistry(dataRoot);
        Optional<User> loaded = registryB.findByUsername(Username.of("frodo"));

        assertTrue(loaded.isPresent());
        assertEquals("frodo", loaded.get().getUsername().getValue());
        assertTrue(loaded.get().getPassword().matches("baggins"));
    }

    @Test
    void lookupIsCaseInsensitive() throws Exception {
        JsonUserRegistry registry = new JsonUserRegistry(tempDir);
        User user = User.of(Username.of("Legolas"), Password.hash("elven", 1000));

        registry.register(user);

        Optional<User> loaded = registry.findByUsername(Username.of("legolas"));
        assertTrue(loaded.isPresent());
    }
}
