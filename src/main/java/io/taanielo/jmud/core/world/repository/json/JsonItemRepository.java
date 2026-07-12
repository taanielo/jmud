package io.taanielo.jmud.core.world.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.reload.ItemContentReloader;
import io.taanielo.jmud.core.reload.PreparedItemReload;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.dto.ItemDto;
import io.taanielo.jmud.core.world.dto.ItemMapper;
import io.taanielo.jmud.core.world.dto.SchemaVersions;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

@Slf4j
public class JsonItemRepository implements ItemRepository, ItemContentReloader {

    private static final String ITEMS_DIR = "items";

    private final ObjectMapper objectMapper;
    private final ItemMapper itemMapper;
    private final Path itemsDirPath;
    private volatile Map<ItemId, Item> cache;

    public JsonItemRepository() throws RepositoryException {
        this(Path.of("data"));
    }

    public JsonItemRepository(Path dataRoot) throws RepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.itemMapper = new ItemMapper();
        this.cache = new ConcurrentHashMap<>();
        this.itemsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(ITEMS_DIR);
        ensureDirectory(itemsDirPath);
    }

    @Override
    public void save(Item item) throws RepositoryException {
        Objects.requireNonNull(item, "Item is required");
        Path itemFilePath = itemFilePath(item.getId());
        ItemDto dto = itemMapper.toDto(item);
        writeItemDto(itemFilePath, dto);
        cache.put(item.getId(), item);
    }

    @Override
    public Optional<Item> findById(ItemId id) throws RepositoryException {
        Objects.requireNonNull(id, "Item id is required");
        Item cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path itemFilePath = itemFilePath(id);
        if (!Files.exists(itemFilePath)) {
            return Optional.empty();
        }
        ItemDto dto = readItemDto(itemFilePath);
        validateSchema(dto, itemFilePath);
        Item item;
        try {
            item = itemMapper.toDomain(dto);
        } catch (IllegalArgumentException e) {
            throw new RepositoryException("Invalid item data in " + itemFilePath + ": " + e.getMessage(), e);
        }
        cache.put(item.getId(), item);
        return Optional.of(item);
    }

    /**
     * Reads and validates every item JSON file into an in-memory snapshot without mutating the live
     * cache (issue #349). The returned {@link PreparedItemReload#commit()} atomically swaps the
     * cache and must be called on the tick thread (AGENTS.md §5).
     */
    @Override
    public PreparedItemReload prepareItems() throws RepositoryException {
        Map<ItemId, Item> loaded = readAllItems();
        // The commit lambda swaps the cache field from within this repository's own method so the
        // arch rule guarding Json*Repository access stays satisfied (AGENTS.md §3.3).
        return PreparedItemReload.of(
            loaded.size(),
            id -> Optional.ofNullable(loaded.get(id)),
            () -> cache = new ConcurrentHashMap<>(loaded));
    }

    private Map<ItemId, Item> readAllItems() throws RepositoryException {
        Map<ItemId, Item> loaded = new ConcurrentHashMap<>();
        try (Stream<Path> files = Files.list(itemsDirPath)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                ItemDto dto = readItemDto(path);
                validateSchema(dto, path);
                Item item;
                try {
                    item = itemMapper.toDomain(dto);
                } catch (IllegalArgumentException e) {
                    throw new RepositoryException("Invalid item data in " + path + ": " + e.getMessage(), e);
                }
                loaded.put(item.getId(), item);
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed to list item data files: " + e.getMessage(), e);
        }
        return loaded;
    }

    private void ensureDirectory(Path path) throws RepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RepositoryException("Failed to create items directory " + path, e);
        }
    }

    private Path itemFilePath(ItemId id) {
        return itemsDirPath.resolve(id.getValue() + ".json");
    }

    private ItemDto readItemDto(Path path) throws RepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), ItemDto.class);
        } catch (IOException e) {
            throw new RepositoryException("Failed to read item data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void writeItemDto(Path path, ItemDto dto) throws RepositoryException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(itemsDirPath, dto.id() + "-", ".tmp");
            objectMapper.writeValue(tempFile.toFile(), dto);
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RepositoryException("Failed to write item data to " + path + ": " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // Best-effort cleanup: the atomic move usually already consumed the temp file,
                    // so a failure here only leaves a stray *.tmp behind and must not mask the
                    // original write outcome. Log so leaked temp files are visible (AGENTS.md §7).
                    log.warn("Failed to delete temporary item file {}", tempFile, e);
                }
            }
        }
    }

    private void validateSchema(ItemDto dto, Path path) throws RepositoryException {
        if (dto.schemaVersion() != SchemaVersions.V3
            && dto.schemaVersion() != SchemaVersions.V4
            && dto.schemaVersion() != SchemaVersions.V5
            && dto.schemaVersion() != SchemaVersions.V6
            && dto.schemaVersion() != SchemaVersions.V7
            && dto.schemaVersion() != SchemaVersions.V8
            && dto.schemaVersion() != SchemaVersions.V9
            && dto.schemaVersion() != SchemaVersions.V10
            && dto.schemaVersion() != SchemaVersions.V11
            && dto.schemaVersion() != SchemaVersions.V12) {
            throw new RepositoryException("Unsupported item schema version " + dto.schemaVersion() + " in " + path);
        }
    }
}
