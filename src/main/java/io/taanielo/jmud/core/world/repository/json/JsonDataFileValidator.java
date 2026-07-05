package io.taanielo.jmud.core.world.repository.json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;

/**
 * Validates that a single JSON file is syntactically valid and parseable using
 * the project-standard Jackson configuration (snake_case, Java time support).
 *
 * <p>This class lives in the {@code repository.json} layer, which is the only
 * layer allowed to use Jackson directly (AGENTS.md §3.2, ArchitectureTest
 * {@code jackson_containment}). Callers outside that layer must use this class
 * rather than instantiating an {@link ObjectMapper} themselves.
 */
@NullMarked
public final class JsonDataFileValidator {

    private final ObjectMapper objectMapper;

    /** Creates a validator backed by the standard project Jackson configuration. */
    public JsonDataFileValidator() {
        this.objectMapper = JsonDataMapper.create();
    }

    /**
     * Attempts to parse the given file as JSON.
     *
     * @param file the file to validate; must exist and be readable
     * @return an empty {@link Optional} on success, or an {@link Optional} containing
     *         the error message on parse failure
     */
    public Optional<String> validate(Path file) {
        try {
            objectMapper.readTree(file.toFile());
            return Optional.empty();
        } catch (IOException e) {
            String message = e.getMessage();
            return Optional.of(message != null ? message : e.getClass().getSimpleName());
        }
    }
}
