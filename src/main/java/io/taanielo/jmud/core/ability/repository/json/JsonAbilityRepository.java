package io.taanielo.jmud.core.ability.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.dto.AbilityDto;
import io.taanielo.jmud.core.ability.dto.AbilityMapper;
import io.taanielo.jmud.core.ability.dto.SchemaVersions;
import io.taanielo.jmud.core.ability.repository.AbilityRepository;
import io.taanielo.jmud.core.ability.repository.AbilityRepositoryException;

public class JsonAbilityRepository implements AbilityRepository {

    private static final String SKILLS_DIR = "skills";

    private final ObjectMapper objectMapper;
    private final AbilityMapper abilityMapper;
    private final Path skillsDirPath;
    private final Map<String, Ability> cache;

    public JsonAbilityRepository() throws AbilityRepositoryException {
        this(Path.of("data"));
    }

    public JsonAbilityRepository(Path dataRoot) throws AbilityRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.abilityMapper = new AbilityMapper();
        this.cache = new ConcurrentHashMap<>();
        this.skillsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(SKILLS_DIR);
        ensureDirectory(skillsDirPath);
    }

    @Override
    public Optional<Ability> findById(String id) throws AbilityRepositoryException {
        if (id == null || id.isBlank()) {
            throw new AbilityRepositoryException("Ability id is required");
        }
        Ability cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path abilityFilePath = abilityFilePath(id);
        if (!Files.exists(abilityFilePath)) {
            return Optional.empty();
        }
        AbilityDto dto = readAbilityDto(abilityFilePath);
        validateSchema(dto, abilityFilePath);
        Ability ability;
        try {
            ability = abilityMapper.toDomain(dto);
        } catch (IllegalArgumentException e) {
            throw new AbilityRepositoryException("Invalid ability data in " + abilityFilePath + ": " + e.getMessage(), e);
        }
        cache.put(ability.id(), ability);
        return Optional.of(ability);
    }

    @Override
    public List<Ability> findAll() throws AbilityRepositoryException {
        List<Ability> abilities = new ArrayList<>();
        try {
            if (!Files.exists(skillsDirPath)) {
                return List.of();
            }
            try (var stream = Files.list(skillsDirPath)) {
                for (Path path : stream.filter(path -> path.toString().endsWith(".json")).toList()) {
                    AbilityDto dto = readAbilityDto(path);
                    validateSchema(dto, path);
                    Ability ability;
                    try {
                        ability = abilityMapper.toDomain(dto);
                    } catch (IllegalArgumentException e) {
                        throw new AbilityRepositoryException("Invalid ability data in " + path + ": " + e.getMessage(), e);
                    }
                    abilities.add(ability);
                    cache.put(ability.id(), ability);
                }
            }
        } catch (IOException e) {
            throw new AbilityRepositoryException("Failed to read abilities from " + skillsDirPath + ": " + e.getMessage(), e);
        }
        return List.copyOf(abilities);
    }

    private void ensureDirectory(Path path) throws AbilityRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new AbilityRepositoryException("Failed to create skills directory " + path, e);
        }
    }

    private Path abilityFilePath(String id) {
        return skillsDirPath.resolve(id + ".json");
    }

    private AbilityDto readAbilityDto(Path path) throws AbilityRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), AbilityDto.class);
        } catch (IOException e) {
            throw new AbilityRepositoryException("Failed to read ability data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void validateSchema(AbilityDto dto, Path path) throws AbilityRepositoryException {
        if (dto.schemaVersion() != SchemaVersions.V1) {
            throw new AbilityRepositoryException(
                "Unsupported ability schema version " + dto.schemaVersion() + " in " + path
            );
        }
    }
}
