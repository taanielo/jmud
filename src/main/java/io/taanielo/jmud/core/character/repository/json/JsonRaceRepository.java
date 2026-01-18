package io.taanielo.jmud.core.character.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.dto.RaceDto;
import io.taanielo.jmud.core.character.dto.RaceMapper;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;

public class JsonRaceRepository implements RaceRepository {
    private static final String RACES_DIR = "races";

    private final ObjectMapper objectMapper;
    private final RaceMapper mapper;
    private final Path racesDirPath;
    private final ConcurrentHashMap<RaceId, Race> cache = new ConcurrentHashMap<>();

    public JsonRaceRepository() throws RaceRepositoryException {
        this(Path.of("data"));
    }

    public JsonRaceRepository(Path dataRoot) throws RaceRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.mapper = new RaceMapper();
        this.racesDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(RACES_DIR);
        ensureDirectory(racesDirPath);
    }

    @Override
    public Optional<Race> findById(RaceId id) throws RaceRepositoryException {
        Objects.requireNonNull(id, "Race id is required");
        Race cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path raceFilePath = raceFilePath(id);
        if (!Files.exists(raceFilePath)) {
            return Optional.empty();
        }
        RaceDto dto = readDto(raceFilePath);
        Race race;
        try {
            race = mapper.toDomain(dto);
        } catch (IllegalArgumentException e) {
            throw new RaceRepositoryException("Invalid race data in " + raceFilePath + ": " + e.getMessage(), e);
        }
        cache.put(id, race);
        return Optional.of(race);
    }

    private void ensureDirectory(Path path) throws RaceRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RaceRepositoryException("Failed to create races directory " + path, e);
        }
    }

    private Path raceFilePath(RaceId id) {
        return racesDirPath.resolve("race." + id.getValue() + ".json");
    }

    private RaceDto readDto(Path path) throws RaceRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), RaceDto.class);
        } catch (IOException e) {
            throw new RaceRepositoryException("Failed to read race data from " + path + ": " + e.getMessage(), e);
        }
    }
}
