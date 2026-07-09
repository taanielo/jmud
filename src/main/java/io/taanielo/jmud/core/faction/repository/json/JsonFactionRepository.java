package io.taanielo.jmud.core.faction.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.FactionRepository;
import io.taanielo.jmud.core.faction.FactionRepositoryException;

/**
 * Loads {@link Faction} definitions from {@code data/factions/*.json} files.
 *
 * <p>All factions are eagerly loaded and cached at construction time so lookups never touch disk on
 * the tick thread (AGENTS.md §5).
 */
@Slf4j
public class JsonFactionRepository implements FactionRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final int DEFAULT_KILL_REPUTATION_DELTA = -10;
    private static final int DEFAULT_HOSTILE_THRESHOLD = 0;
    private static final double DEFAULT_PRICE_MODIFIER_PER_POINT = 0.0;
    private static final String FACTIONS_DIR = "factions";

    private final ObjectMapper objectMapper;
    private final Path factionsDirPath;
    private final List<Faction> cache;

    public JsonFactionRepository() throws FactionRepositoryException {
        this(Path.of("data"));
    }

    public JsonFactionRepository(Path dataRoot) throws FactionRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.factionsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(FACTIONS_DIR);
        ensureDirectory(factionsDirPath);
        this.cache = load();
    }

    @Override
    public List<Faction> findAll() {
        return cache;
    }

    @Override
    public Optional<Faction> findById(FactionId factionId) {
        Objects.requireNonNull(factionId, "factionId is required");
        return cache.stream().filter(f -> f.id().equals(factionId)).findFirst();
    }

    // ── private helpers ───────────────────────────────────────────────

    private List<Faction> load() throws FactionRepositoryException {
        List<Faction> factions = new ArrayList<>();
        try (var stream = Files.list(factionsDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                FactionDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new FactionRepositoryException(
                        "Unsupported faction schema version " + dto.schemaVersion() + " in " + path);
                }
                factions.add(toDomain(dto, path));
            }
        } catch (IOException e) {
            throw new FactionRepositoryException("Failed to list faction data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} faction(s) from {}", factions.size(), factionsDirPath);
        return List.copyOf(factions);
    }

    private Faction toDomain(FactionDto dto, Path source) throws FactionRepositoryException {
        try {
            return new Faction(
                FactionId.of(Objects.requireNonNull(dto.id(), "Faction id is required")),
                Objects.requireNonNull(dto.name(), "Faction name is required"),
                dto.description() != null ? dto.description() : "",
                dto.killReputationDelta() != null ? dto.killReputationDelta() : DEFAULT_KILL_REPUTATION_DELTA,
                dto.hostileThreshold() != null ? dto.hostileThreshold() : DEFAULT_HOSTILE_THRESHOLD,
                dto.priceModifierPerPoint() != null ? dto.priceModifierPerPoint() : DEFAULT_PRICE_MODIFIER_PER_POINT
            );
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new FactionRepositoryException("Invalid faction data in " + source + ": " + e.getMessage(), e);
        }
    }

    private FactionDto readDto(Path path) throws FactionRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), FactionDto.class);
        } catch (IOException e) {
            throw new FactionRepositoryException("Failed to read faction data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws FactionRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new FactionRepositoryException("Failed to create factions directory " + path, e);
        }
    }
}
