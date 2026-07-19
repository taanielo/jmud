package io.taanielo.jmud.core.creation.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.creation.NewPlayerHints;
import io.taanielo.jmud.core.creation.NewPlayerHintsException;
import io.taanielo.jmud.core.creation.NewPlayerHintsRepository;

/**
 * Loads the {@link NewPlayerHints} definition from {@code data/new-player-hints.json}.
 *
 * <p>The single file is eagerly loaded and cached on first access, so no blocking I/O reaches the
 * tick loop (AGENTS.md §5). Blank lines are rejected so the onboarding block never renders empty
 * rows.
 */
@Slf4j
public class JsonNewPlayerHintsRepository implements NewPlayerHintsRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String HINTS_FILE = "new-player-hints.json";

    private final ObjectMapper objectMapper;
    private final Path hintsFilePath;
    @Nullable
    private NewPlayerHints cache;

    /** Creates a repository loading the hints file from the default {@code data} directory. */
    public JsonNewPlayerHintsRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository loading the hints file from a specific data root, allowing tests to point
     * at a fixture directory.
     *
     * @param dataRoot the data root directory containing {@code new-player-hints.json}
     */
    public JsonNewPlayerHintsRepository(Path dataRoot) {
        this.objectMapper = createMapper();
        this.hintsFilePath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(HINTS_FILE);
    }

    @Override
    public NewPlayerHints load() throws NewPlayerHintsException {
        NewPlayerHints loaded = cache;
        if (loaded == null) {
            loaded = readAndMap();
            cache = loaded;
        }
        return loaded;
    }

    private NewPlayerHints readAndMap() throws NewPlayerHintsException {
        if (!Files.exists(hintsFilePath)) {
            throw new NewPlayerHintsException("New-player hints file not found at " + hintsFilePath);
        }
        NewPlayerHintsDto dto = readDto();
        if (dto.schemaVersion() != SCHEMA_VERSION) {
            throw new NewPlayerHintsException(
                "Unsupported new-player hints schema version " + dto.schemaVersion() + " in " + hintsFilePath);
        }
        String title = dto.title();
        if (title == null || title.isBlank()) {
            throw new NewPlayerHintsException("New-player hints title must not be blank in " + hintsFilePath);
        }
        List<String> rawLines = Objects.requireNonNullElse(dto.lines(), List.<String>of());
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            if (line == null || line.isBlank()) {
                throw new NewPlayerHintsException(
                    "New-player hints contains a blank line in " + hintsFilePath);
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            throw new NewPlayerHintsException("New-player hints must define at least one line in " + hintsFilePath);
        }
        NewPlayerHints hints = new NewPlayerHints(title, lines);
        log.info("Loaded new-player hints ('{}', {} line(s)) from {}", hints.title(), hints.lines().size(),
            hintsFilePath);
        return hints;
    }

    private NewPlayerHintsDto readDto() throws NewPlayerHintsException {
        try {
            return objectMapper.readValue(hintsFilePath.toFile(), NewPlayerHintsDto.class);
        } catch (IOException e) {
            throw new NewPlayerHintsException(
                "Failed to read new-player hints data from " + hintsFilePath + ": " + e.getMessage(), e);
        }
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
