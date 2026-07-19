package io.taanielo.jmud.core.world.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.world.ItemSet;
import io.taanielo.jmud.core.world.ItemSetId;
import io.taanielo.jmud.core.world.dto.ItemSetDto;
import io.taanielo.jmud.core.world.dto.ItemSetMapper;
import io.taanielo.jmud.core.world.repository.ItemSetRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Loads item-set definitions from one JSON file per set under {@code data/item-sets/} (issue #771).
 * The directory is read and cached on first access; a missing directory yields an empty set catalog
 * so a world with no item sets loads unchanged.
 */
public class JsonItemSetRepository implements ItemSetRepository {

    /** Supported schema version of {@code data/item-sets/*.json}. */
    static final int ITEM_SET_SCHEMA_VERSION = 1;

    private static final String ITEM_SETS_DIR = "item-sets";

    private final ObjectMapper objectMapper;
    private final ItemSetMapper mapper;
    private final Path itemSetsDirPath;

    private Map<ItemSetId, ItemSet> cache;

    public JsonItemSetRepository() {
        this(Path.of("data"));
    }

    public JsonItemSetRepository(Path dataRoot) {
        this.objectMapper = JsonDataMapper.create();
        this.mapper = new ItemSetMapper();
        this.itemSetsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(ITEM_SETS_DIR);
    }

    @Override
    public Optional<ItemSet> findById(ItemSetId id) throws RepositoryException {
        Objects.requireNonNull(id, "Item set id is required");
        return Optional.ofNullable(load().get(id));
    }

    @Override
    public List<ItemSet> findAll() throws RepositoryException {
        return List.copyOf(load().values());
    }

    private synchronized Map<ItemSetId, ItemSet> load() throws RepositoryException {
        if (cache != null) {
            return cache;
        }
        if (!Files.exists(itemSetsDirPath) || !Files.isDirectory(itemSetsDirPath)) {
            cache = Map.of();
            return cache;
        }
        Map<ItemSetId, ItemSet> loaded = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(itemSetsDirPath)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                ItemSetDto dto = readDto(path);
                if (dto.schemaVersion() != ITEM_SET_SCHEMA_VERSION) {
                    throw new RepositoryException(
                        "Unsupported item-set schema version " + dto.schemaVersion() + " in " + path);
                }
                ItemSet set;
                try {
                    set = mapper.toDomain(dto);
                } catch (IllegalArgumentException e) {
                    throw new RepositoryException("Invalid item-set data in " + path + ": " + e.getMessage(), e);
                }
                if (loaded.put(set.id(), set) != null) {
                    throw new RepositoryException("Duplicate item-set id '" + set.id().getValue() + "' in " + path);
                }
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed to list item-set data files: " + e.getMessage(), e);
        }
        cache = Map.copyOf(loaded);
        return cache;
    }

    private ItemSetDto readDto(Path path) throws RepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), ItemSetDto.class);
        } catch (IOException e) {
            throw new RepositoryException("Failed to read item-set data from " + path + ": " + e.getMessage(), e);
        }
    }
}
