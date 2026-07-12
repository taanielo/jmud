package io.taanielo.jmud.core.world.area.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.area.Area;
import io.taanielo.jmud.core.world.area.AreaId;
import io.taanielo.jmud.core.world.area.AreaRepository;
import io.taanielo.jmud.core.world.area.WorldAtlas;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonDataMapper;

/**
 * Loads world {@link Area} definitions from {@code data/areas/*.json} and the {@link WorldAtlas}
 * from {@code data/areas/atlas.json}. All entries are eagerly loaded and cached on first access.
 */
@Slf4j
public class JsonAreaRepository implements AreaRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String AREAS_DIR = "areas";
    private static final String ATLAS_FILE = "atlas.json";

    private final ObjectMapper objectMapper;
    private final Path areasDirPath;
    private List<Area> areaCache;
    private Optional<WorldAtlas> atlasCache;

    /** Creates a repository rooted at the default {@code data} directory. */
    public JsonAreaRepository() throws RepositoryException {
        this(Path.of("data"));
    }

    /**
     * Creates a repository rooted at the given data directory.
     *
     * @param dataRoot the root of the game data tree
     */
    public JsonAreaRepository(Path dataRoot) throws RepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.areasDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(AREAS_DIR);
        ensureDirectory(areasDirPath);
    }

    @Override
    public List<Area> findAll() throws RepositoryException {
        if (areaCache == null) {
            areaCache = loadAreas();
        }
        return areaCache;
    }

    @Override
    public Optional<Area> findById(AreaId id) throws RepositoryException {
        Objects.requireNonNull(id, "Area id is required");
        return findAll().stream().filter(a -> a.id().equals(id)).findFirst();
    }

    @Override
    public Optional<WorldAtlas> findAtlas() throws RepositoryException {
        if (atlasCache == null) {
            atlasCache = loadAtlas();
        }
        return atlasCache;
    }

    private List<Area> loadAreas() throws RepositoryException {
        List<Area> areas = new ArrayList<>();
        try (var stream = Files.list(areasDirPath)) {
            for (Path path : stream
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().equals(ATLAS_FILE))
                .toList()) {
                AreaDto dto = readDto(path, AreaDto.class);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new RepositoryException(
                        "Unsupported area schema version " + dto.schemaVersion() + " in " + path);
                }
                areas.add(toDomain(dto, path));
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed to list area data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} area(s) from {}", areas.size(), areasDirPath);
        return List.copyOf(areas);
    }

    private Optional<WorldAtlas> loadAtlas() throws RepositoryException {
        Path atlasPath = areasDirPath.resolve(ATLAS_FILE);
        if (!Files.exists(atlasPath)) {
            return Optional.empty();
        }
        AtlasDto dto = readDto(atlasPath, AtlasDto.class);
        if (dto.schemaVersion() != SCHEMA_VERSION) {
            throw new RepositoryException(
                "Unsupported atlas schema version " + dto.schemaVersion() + " in " + atlasPath);
        }
        try {
            return Optional.of(new WorldAtlas(dto.id(), dto.name(), dto.asciiMap()));
        } catch (IllegalArgumentException e) {
            throw new RepositoryException("Invalid atlas data in " + atlasPath + ": " + e.getMessage(), e);
        }
    }

    private Area toDomain(AreaDto dto, Path source) throws RepositoryException {
        try {
            List<RoomId> roomIds = dto.roomIds() == null
                ? List.of()
                : dto.roomIds().stream().map(RoomId::of).toList();
            List<AreaId> connections = dto.connections() == null
                ? List.of()
                : dto.connections().stream().map(AreaId::of).toList();
            List<String> asciiMap = dto.asciiMap() == null ? List.of() : dto.asciiMap();
            return new Area(AreaId.of(dto.id()), dto.name(), roomIds, connections, asciiMap);
        } catch (IllegalArgumentException e) {
            throw new RepositoryException("Invalid area data in " + source + ": " + e.getMessage(), e);
        }
    }

    private <T> T readDto(Path path, Class<T> type) throws RepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new RepositoryException("Failed to read area data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws RepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RepositoryException("Failed to create areas directory " + path, e);
        }
    }
}
