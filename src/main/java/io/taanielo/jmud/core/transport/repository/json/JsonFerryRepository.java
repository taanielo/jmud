package io.taanielo.jmud.core.transport.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.transport.Ferry;
import io.taanielo.jmud.core.transport.FerryId;
import io.taanielo.jmud.core.transport.FerryRepository;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Loads {@link Ferry} definitions from {@code data/ferries/*.json}.
 *
 * <p>All ferries are eagerly loaded at construction and returned in file-name order so the
 * schedule is reproducible. This repository is read-only: ferry routes are static game content
 * (AGENTS.md §11), so there is no write path and nothing here is reachable from {@code tick()}.
 */
@Slf4j
public class JsonFerryRepository implements FerryRepository {

    /** The only schema version currently supported for ferry definition files. */
    public static final int SCHEMA_VERSION = 1;
    private static final String FERRIES_DIR = "ferries";

    private final List<Ferry> ferries;

    /**
     * Creates a repository rooted at {@code data}, loading all ferry definitions.
     */
    public JsonFerryRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository rooted at the given data directory, loading all ferry definitions.
     *
     * @param dataRoot the root data directory (e.g. {@code data})
     * @throws FerryRepositoryException if the ferries directory cannot be read or contains
     *                                  invalid data
     */
    public JsonFerryRepository(Path dataRoot) {
        Path ferriesDir = Objects.requireNonNull(dataRoot, "Data root is required").resolve(FERRIES_DIR);
        ObjectMapper objectMapper = JsonDataMapper.create();
        this.ferries = loadAll(ferriesDir, objectMapper);
        log.info("Loaded {} ferry definition(s) from {}", ferries.size(), ferriesDir);
    }

    @Override
    public List<Ferry> findAll() {
        return ferries;
    }

    private static List<Ferry> loadAll(Path ferriesDir, ObjectMapper objectMapper) {
        if (!Files.isDirectory(ferriesDir)) {
            return List.of();
        }
        List<Ferry> loaded = new ArrayList<>();
        try (var stream = Files.list(ferriesDir)) {
            List<Path> files = stream
                .filter(p -> p.toString().endsWith(".json"))
                .sorted()
                .toList();
            for (Path file : files) {
                loaded.add(readFerry(file, objectMapper));
            }
        } catch (IOException e) {
            throw new FerryRepositoryException("Failed to list ferry data files in " + ferriesDir, e);
        }
        return List.copyOf(loaded);
    }

    private static Ferry readFerry(Path path, ObjectMapper objectMapper) {
        FerryDto dto;
        try {
            dto = objectMapper.readValue(path.toFile(), FerryDto.class);
        } catch (IOException e) {
            throw new FerryRepositoryException("Failed to read ferry data from " + path + ": " + e.getMessage(), e);
        }
        if (dto.schemaVersion() != SCHEMA_VERSION) {
            throw new FerryRepositoryException(
                "Unsupported ferry schema version " + dto.schemaVersion() + " in " + path);
        }
        try {
            List<String> routeIds = Objects.requireNonNull(dto.route(), "route is required");
            List<RoomId> route = new ArrayList<>(routeIds.size());
            for (String dockId : routeIds) {
                route.add(RoomId.of(Objects.requireNonNull(dockId, "route dock id is required")));
            }
            return new Ferry(
                FerryId.of(Objects.requireNonNull(dto.id(), "id is required")),
                Objects.requireNonNull(dto.name(), "name is required"),
                RoomId.of(Objects.requireNonNull(dto.deckRoomId(), "deck_room_id is required")),
                route,
                dto.ticksPerLeg(),
                dto.startLegIndex() == null ? 0 : dto.startLegIndex(),
                dto.departureMessages() == null ? List.of() : dto.departureMessages(),
                dto.arrivalMessages() == null ? List.of() : dto.arrivalMessages()
            );
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new FerryRepositoryException("Invalid ferry data in " + path + ": " + e.getMessage(), e);
        }
    }
}
