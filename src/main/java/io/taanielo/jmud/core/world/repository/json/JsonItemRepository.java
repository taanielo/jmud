package io.taanielo.jmud.core.world.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.dto.ItemDto;
import io.taanielo.jmud.core.world.dto.ItemMapper;
import io.taanielo.jmud.core.world.dto.SchemaVersions;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

public class JsonItemRepository implements ItemRepository {

    private static final String ITEMS_DIR = "items";

    private final ObjectMapper objectMapper;
    private final ItemMapper itemMapper;
    private final Path itemsDirPath;
    private final Map<ItemId, Item> cache;

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
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void validateSchema(ItemDto dto, Path path) throws RepositoryException {
        if (dto.schemaVersion() != SchemaVersions.V2) {
            throw new RepositoryException("Unsupported item schema version " + dto.schemaVersion() + " in " + path);
        }
    }
}
