package io.taanielo.jmud.core.gathering.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.gathering.ResourceNode;
import io.taanielo.jmud.core.gathering.ResourceNodeRepository;
import io.taanielo.jmud.core.gathering.dto.ResourceNodeDto;
import io.taanielo.jmud.core.gathering.dto.ResourceNodeMapper;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Loads {@link ResourceNode} definitions from {@code data/resource-nodes/*.json} files.
 *
 * <p>All nodes are eagerly loaded and cached on first access, following the
 * {@code JsonRecipeRepository} convention.
 */
@Slf4j
public class JsonResourceNodeRepository implements ResourceNodeRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String NODES_DIR = "resource-nodes";

    private final ObjectMapper objectMapper;
    private final ResourceNodeMapper nodeMapper;
    private final Path nodesDirPath;
    @Nullable
    private List<ResourceNode> cache;

    public JsonResourceNodeRepository() throws RepositoryException {
        this(Path.of("data"));
    }

    public JsonResourceNodeRepository(Path dataRoot) throws RepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.nodeMapper = new ResourceNodeMapper();
        this.nodesDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(NODES_DIR);
        ensureDirectory(nodesDirPath);
    }

    @Override
    public List<ResourceNode> findAll() throws RepositoryException {
        List<ResourceNode> loaded = cache;
        if (loaded == null) {
            loaded = load();
            cache = loaded;
        }
        return loaded;
    }

    private List<ResourceNode> load() throws RepositoryException {
        List<ResourceNode> nodes = new ArrayList<>();
        try (var stream = Files.list(nodesDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                ResourceNodeDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new RepositoryException(
                        "Unsupported resource node schema version " + dto.schemaVersion() + " in " + path);
                }
                try {
                    nodes.add(nodeMapper.toDomain(dto));
                } catch (IllegalArgumentException e) {
                    throw new RepositoryException(
                        "Invalid resource node data in " + path + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed to list resource node data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} resource node(s) from {}", nodes.size(), nodesDirPath);
        return List.copyOf(nodes);
    }

    private ResourceNodeDto readDto(Path path) throws RepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), ResourceNodeDto.class);
        } catch (IOException e) {
            throw new RepositoryException(
                "Failed to read resource node data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws RepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RepositoryException("Failed to create resource-nodes directory " + path, e);
        }
    }
}
