package io.taanielo.jmud.core.authentication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.world.repository.json.JsonDataMapper;

/**
 * JSON-backed {@link UserRegistry} that persists each user as an individual
 * file under {@code data/users/}.
 *
 * <p>Writes are atomic: the new content is first written to a sibling
 * {@code .tmp} file and then renamed over the target file, ensuring that a
 * concurrent reader never observes a partially-written state.
 *
 * <p>An in-memory {@link ConcurrentHashMap} cache is kept so that
 * {@link #findByUsername} does not require a disk read after the first load.
 */
@Slf4j
public class JsonUserRegistry implements UserRegistry {

    private static final String USERS_DIR = "users";

    private final ObjectMapper objectMapper;
    private final Path usersDirPath;
    private final Map<Username, User> cache;

    /**
     * Creates a registry that stores user files under {@code data/users/}
     * relative to the current working directory.
     *
     * @throws UserRegistryException if the users directory cannot be created
     */
    public JsonUserRegistry() throws UserRegistryException {
        this(Path.of("data"));
    }

    /**
     * Creates a registry that stores user files under {@code <dataRoot>/users/}.
     *
     * @param dataRoot root of the data directory tree
     * @throws UserRegistryException if the users directory cannot be created
     */
    public JsonUserRegistry(Path dataRoot) throws UserRegistryException {
        this.objectMapper = JsonDataMapper.create();
        this.cache = new ConcurrentHashMap<>();
        this.usersDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(USERS_DIR);
        ensureDirectory(usersDirPath);
    }

    /**
     * Returns the user with the given username, loading from disk on first
     * access and caching the result.
     *
     * @param username the username to look up
     * @return an {@link Optional} containing the user, or empty if not found
     */
    @Override
    public Optional<User> findByUsername(Username username) {
        Objects.requireNonNull(username, "Username is required");
        User cached = cache.get(username);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path filePath = userFilePath(username);
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            User user = objectMapper.readValue(filePath.toFile(), User.class);
            cache.put(user.getUsername(), user);
            return Optional.of(user);
        } catch (IOException e) {
            log.error("Failed to load user {} from {}", username.getValue(), filePath, e);
            return Optional.empty();
        }
    }

    /**
     * Persists a new user to disk.  If a file for this username already exists
     * it is left untouched (first-registered-wins semantics, matching the
     * in-memory implementation).
     *
     * @param user the user to register
     */
    @Override
    public void register(User user) {
        Objects.requireNonNull(user, "User is required");
        Path filePath = userFilePath(user.getUsername());
        if (Files.exists(filePath)) {
            // File already exists on disk — first registration wins; do not overwrite.
            // Ensure cache reflects the on-disk user (loaded lazily on next findByUsername).
            return;
        }
        if (cache.putIfAbsent(user.getUsername(), user) != null) {
            // Already present in cache — do not overwrite.
            return;
        }
        try {
            writeUser(filePath, user);
            log.info("User {} registered and saved to {}", user.getUsername().getValue(), filePath);
        } catch (UserRegistryException e) {
            log.error("Failed to persist user {}", user.getUsername().getValue(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path userFilePath(Username username) {
        return usersDirPath.resolve(username.getValue().toLowerCase() + ".json");
    }

    private void writeUser(Path filePath, User user) throws UserRegistryException {
        Path tempFile = filePath.getParent().resolve(filePath.getFileName() + ".tmp");
        try {
            objectMapper.writeValue(tempFile.toFile(), user);
            Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            throw new UserRegistryException(
                "Failed to write user " + user.getUsername().getValue() + " to " + filePath + ": " + e.getMessage(), e
            );
        }
    }

    private void ensureDirectory(Path path) throws UserRegistryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UserRegistryException("Failed to create users directory " + path + ": " + e.getMessage(), e);
        }
    }
}
