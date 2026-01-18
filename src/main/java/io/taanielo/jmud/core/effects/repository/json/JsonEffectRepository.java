package io.taanielo.jmud.core.effects.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.dto.EffectDefinitionDto;
import io.taanielo.jmud.core.effects.dto.EffectDefinitionMapper;
import io.taanielo.jmud.core.world.repository.json.JsonDataMapper;

public class JsonEffectRepository implements EffectRepository {

    private static final String EFFECTS_DIR = "effects";

    private final ObjectMapper objectMapper;
    private final EffectDefinitionMapper mapper;
    private final Path effectsDirPath;
    private final Map<EffectId, EffectDefinition> cache;

    public JsonEffectRepository() throws EffectRepositoryException {
        this(Path.of("."));
    }

    public JsonEffectRepository(Path dataRoot) throws EffectRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.mapper = new EffectDefinitionMapper();
        this.cache = new ConcurrentHashMap<>();
        this.effectsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(EFFECTS_DIR);
        ensureDirectory(effectsDirPath);
    }

    @Override
    public Optional<EffectDefinition> findById(EffectId id) throws EffectRepositoryException {
        Objects.requireNonNull(id, "Effect id is required");
        EffectDefinition cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path effectFilePath = effectFilePath(id);
        if (!Files.exists(effectFilePath)) {
            return Optional.empty();
        }
        EffectDefinitionDto dto = readDto(effectFilePath);
        EffectDefinition definition;
        try {
            definition = mapper.toDomain(dto);
        } catch (IllegalArgumentException e) {
            throw new EffectRepositoryException("Invalid effect data in " + effectFilePath + ": " + e.getMessage(), e);
        }
        cache.put(id, definition);
        return Optional.of(definition);
    }

    private void ensureDirectory(Path path) throws EffectRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new EffectRepositoryException("Failed to create effects directory " + path, e);
        }
    }

    private Path effectFilePath(EffectId id) {
        return effectsDirPath.resolve(id.getValue() + ".json");
    }

    private EffectDefinitionDto readDto(Path path) throws EffectRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), EffectDefinitionDto.class);
        } catch (IOException e) {
            throw new EffectRepositoryException("Failed to read effect data from " + path + ": " + e.getMessage(), e);
        }
    }
}
