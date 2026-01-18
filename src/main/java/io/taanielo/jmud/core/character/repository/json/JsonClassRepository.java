package io.taanielo.jmud.core.character.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.dto.ClassDto;
import io.taanielo.jmud.core.character.dto.ClassMapper;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;

public class JsonClassRepository implements ClassRepository {
    private static final String CLASSES_DIR = "classes";

    private final ObjectMapper objectMapper;
    private final ClassMapper mapper;
    private final Path classesDirPath;
    private final ConcurrentHashMap<ClassId, ClassDefinition> cache = new ConcurrentHashMap<>();

    public JsonClassRepository() throws ClassRepositoryException {
        this(Path.of("data"));
    }

    public JsonClassRepository(Path dataRoot) throws ClassRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.mapper = new ClassMapper();
        this.classesDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(CLASSES_DIR);
        ensureDirectory(classesDirPath);
    }

    @Override
    public Optional<ClassDefinition> findById(ClassId id) throws ClassRepositoryException {
        Objects.requireNonNull(id, "Class id is required");
        ClassDefinition cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path classFilePath = classFilePath(id);
        if (!Files.exists(classFilePath)) {
            return Optional.empty();
        }
        ClassDto dto = readDto(classFilePath);
        ClassDefinition definition;
        try {
            definition = mapper.toDomain(dto);
        } catch (IllegalArgumentException e) {
            throw new ClassRepositoryException("Invalid class data in " + classFilePath + ": " + e.getMessage(), e);
        }
        cache.put(id, definition);
        return Optional.of(definition);
    }

    private void ensureDirectory(Path path) throws ClassRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new ClassRepositoryException("Failed to create classes directory " + path, e);
        }
    }

    private Path classFilePath(ClassId id) {
        return classesDirPath.resolve("class." + id.getValue() + ".json");
    }

    private ClassDto readDto(Path path) throws ClassRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), ClassDto.class);
        } catch (IOException e) {
            throw new ClassRepositoryException("Failed to read class data from " + path + ": " + e.getMessage(), e);
        }
    }
}
