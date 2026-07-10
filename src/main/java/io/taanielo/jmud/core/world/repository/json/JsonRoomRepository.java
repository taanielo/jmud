package io.taanielo.jmud.core.world.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.reload.ItemLookup;
import io.taanielo.jmud.core.reload.PreparedReload;
import io.taanielo.jmud.core.reload.RoomContentReloader;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.dto.RoomDto;
import io.taanielo.jmud.core.world.dto.RoomMapper;
import io.taanielo.jmud.core.world.dto.SchemaVersions;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

public class JsonRoomRepository implements RoomRepository, RoomContentReloader {

    private static final String ROOMS_DIR = "rooms";

    private final ObjectMapper objectMapper;
    private final RoomMapper roomMapper;
    private final ItemRepository itemRepository;
    private final Path roomsDirPath;
    private volatile Map<RoomId, Room> cache;

    public JsonRoomRepository(ItemRepository itemRepository) throws RepositoryException {
        this(itemRepository, Path.of("data"));
    }

    public JsonRoomRepository(ItemRepository itemRepository, Path dataRoot) throws RepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.roomMapper = new RoomMapper();
        this.cache = new ConcurrentHashMap<>();
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        this.roomsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(ROOMS_DIR);
        ensureDirectory(roomsDirPath);
    }

    @Override
    public void save(Room room) throws RepositoryException {
        Objects.requireNonNull(room, "Room is required");
        Path roomFilePath = roomFilePath(room.getId());
        RoomDto dto = roomMapper.toDto(room);
        writeRoomDto(roomFilePath, dto);
        cache.put(room.getId(), room);
    }

    @Override
    public Optional<Room> findById(RoomId id) throws RepositoryException {
        Objects.requireNonNull(id, "Room id is required");
        Room cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path roomFilePath = roomFilePath(id);
        if (!Files.exists(roomFilePath)) {
            return Optional.empty();
        }
        RoomDto dto = readRoomDto(roomFilePath);
        validateSchema(dto, roomFilePath);
        List<Item> items = resolveItems(dto, roomFilePath, itemRepository::findById);
        Room room;
        try {
            room = roomMapper.toDomain(dto, items);
        } catch (IllegalArgumentException e) {
            throw new RepositoryException("Invalid room data in " + roomFilePath + ": " + e.getMessage(), e);
        }
        cache.put(room.getId(), room);
        return Optional.of(room);
    }

    /**
     * Reads and validates every room JSON file into an in-memory snapshot without mutating the live
     * cache (issue #349). Item references are resolved through {@code itemLookup} so a room may
     * reference an item added in the same reload. The returned {@link PreparedReload#commit()}
     * atomically swaps the cache and must be called on the tick thread (AGENTS.md §5).
     */
    @Override
    public PreparedReload prepareRooms(ItemLookup itemLookup) throws RepositoryException {
        Objects.requireNonNull(itemLookup, "Item lookup is required");
        Map<RoomId, Room> loaded = readAllRooms(itemLookup);
        // The commit lambda swaps the cache field from within this repository's own method so the
        // arch rule guarding Json*Repository access stays satisfied (AGENTS.md §3.3).
        return PreparedReload.of("rooms", loaded.size(), () -> cache = new ConcurrentHashMap<>(loaded));
    }

    private Map<RoomId, Room> readAllRooms(ItemLookup itemLookup) throws RepositoryException {
        Map<RoomId, Room> loaded = new ConcurrentHashMap<>();
        try (Stream<Path> files = Files.list(roomsDirPath)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                RoomDto dto = readRoomDto(path);
                validateSchema(dto, path);
                List<Item> items = resolveItems(dto, path, itemLookup);
                Room room;
                try {
                    room = roomMapper.toDomain(dto, items);
                } catch (IllegalArgumentException e) {
                    throw new RepositoryException("Invalid room data in " + path + ": " + e.getMessage(), e);
                }
                loaded.put(room.getId(), room);
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed to list room data files: " + e.getMessage(), e);
        }
        return loaded;
    }

    private void ensureDirectory(Path path) throws RepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RepositoryException("Failed to create rooms directory " + path, e);
        }
    }

    private Path roomFilePath(RoomId id) {
        return roomsDirPath.resolve(id.getValue() + ".json");
    }

    private RoomDto readRoomDto(Path path) throws RepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), RoomDto.class);
        } catch (IOException e) {
            throw new RepositoryException("Failed to read room data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void writeRoomDto(Path path, RoomDto dto) throws RepositoryException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(roomsDirPath, dto.id() + "-", ".tmp");
            objectMapper.writeValue(tempFile.toFile(), dto);
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RepositoryException("Failed to write room data to " + path + ": " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void validateSchema(RoomDto dto, Path path) throws RepositoryException {
        if (dto.schemaVersion() != SchemaVersions.V1
                && dto.schemaVersion() != SchemaVersions.V2
                && dto.schemaVersion() != SchemaVersions.V3
                && dto.schemaVersion() != SchemaVersions.V4
                && dto.schemaVersion() != SchemaVersions.V5
                && dto.schemaVersion() != SchemaVersions.V6
                && dto.schemaVersion() != SchemaVersions.V7
                && dto.schemaVersion() != SchemaVersions.V8) {
            throw new RepositoryException("Unsupported room schema version " + dto.schemaVersion() + " in " + path);
        }
    }

    private List<Item> resolveItems(RoomDto dto, Path path, ItemLookup itemLookup) throws RepositoryException {
        if (dto.itemIds() == null) {
            throw new RepositoryException("Room data in " + path + " is missing item_ids");
        }
        List<Item> items = new ArrayList<>();
        for (String itemIdValue : dto.itemIds()) {
            if (itemIdValue == null || itemIdValue.isBlank()) {
                throw new RepositoryException("Room data in " + path + " contains a blank item id");
            }
            ItemId itemId = ItemId.of(itemIdValue);
            try {
                Optional<Item> item = itemLookup.find(itemId);
                if (item.isEmpty()) {
                    throw new RepositoryException("Room data in " + path + " references missing item id " + itemIdValue);
                }
                items.add(item.get());
            } catch (RepositoryException e) {
                throw new RepositoryException("Failed to resolve item " + itemIdValue + " for room data in " + path, e);
            }
        }
        return items;
    }
}
