package io.taanielo.jmud.core.combat.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.dto.AttackDto;
import io.taanielo.jmud.core.combat.dto.AttackMapper;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;

public class JsonAttackRepository implements AttackRepository {
    private static final String ATTACKS_DIR = "attacks";

    private final ObjectMapper objectMapper;
    private final AttackMapper mapper;
    private final Path attacksDirPath;
    private final ConcurrentHashMap<AttackId, AttackDefinition> cache = new ConcurrentHashMap<>();

    public JsonAttackRepository() throws AttackRepositoryException {
        this(Path.of("data"));
    }

    public JsonAttackRepository(Path dataRoot) throws AttackRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.mapper = new AttackMapper();
        this.attacksDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(ATTACKS_DIR);
        ensureDirectory(attacksDirPath);
    }

    @Override
    public Optional<AttackDefinition> findById(AttackId id) throws AttackRepositoryException {
        Objects.requireNonNull(id, "Attack id is required");
        AttackDefinition cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path attackFilePath = attackFilePath(id);
        if (!Files.exists(attackFilePath)) {
            return Optional.empty();
        }
        AttackDto dto = readDto(attackFilePath);
        AttackDefinition definition;
        try {
            definition = mapper.toDomain(dto);
        } catch (IllegalArgumentException e) {
            throw new AttackRepositoryException("Invalid attack data in " + attackFilePath + ": " + e.getMessage(), e);
        }
        cache.put(id, definition);
        return Optional.of(definition);
    }

    private void ensureDirectory(Path path) throws AttackRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new AttackRepositoryException("Failed to create attacks directory " + path, e);
        }
    }

    private Path attackFilePath(AttackId id) {
        return attacksDirPath.resolve(id.getValue() + ".json");
    }

    private AttackDto readDto(Path path) throws AttackRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), AttackDto.class);
        } catch (IOException e) {
            throw new AttackRepositoryException("Failed to read attack data from " + path + ": " + e.getMessage(), e);
        }
    }
}
