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

import com.fasterxml.jackson.databind.ObjectMapper;

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

public class JsonRoomRepository implements RoomRepository {

    private static final String ROOMS_DIR = "rooms";

    private final ObjectMapper objectMapper;
    private final RoomMapper roomMapper;
    private final ItemRepository itemRepository;
    private final Path roomsDirPath;
    private final Map<RoomId, Room> cache;

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
        List<Item> items = resolveItems(dto, roomFilePath);
        Room room;
        try {
            room = roomMapper.toDomain(dto, items);
        } catch (IllegalArgumentException e) {
            throw new RepositoryException("Invalid room data in " + roomFilePath + ": " + e.getMessage(), e);
        }
        cache.put(room.getId(), room);
        return Optional.of(room);
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
        if (dto.schemaVersion() != SchemaVersions.V1) {
            throw new RepositoryException("Unsupported room schema version " + dto.schemaVersion() + " in " + path);
        }
    }

    private List<Item> resolveItems(RoomDto dto, Path path) throws RepositoryException {
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
                Optional<Item> item = itemRepository.findById(itemId);
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
