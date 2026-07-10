package io.taanielo.jmud.core.salvage.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.salvage.SalvageTier;
import io.taanielo.jmud.core.salvage.SalvageTierRepository;
import io.taanielo.jmud.core.salvage.SalvageTierRepositoryException;
import io.taanielo.jmud.core.salvage.dto.SalvageTierDto;
import io.taanielo.jmud.core.salvage.dto.SalvageTierMapper;
import io.taanielo.jmud.core.salvage.dto.SalvageTiersDto;

/**
 * Loads {@link SalvageTier} definitions from {@code data/salvage/salvage-tiers.json}.
 *
 * <p>The single file is eagerly loaded and cached on first access, so no blocking I/O reaches the
 * tick loop (AGENTS.md §5).
 */
@Slf4j
public class JsonSalvageTierRepository implements SalvageTierRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String SALVAGE_DIR = "salvage";
    private static final String TIERS_FILE = "salvage-tiers.json";

    private final ObjectMapper objectMapper;
    private final SalvageTierMapper tierMapper;
    private final Path tiersFilePath;
    @Nullable
    private List<SalvageTier> cache;

    public JsonSalvageTierRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository loading the salvage tier file from a specific data root, allowing tests to
     * point at a fixture directory.
     *
     * @param dataRoot the data root directory containing the {@code salvage} subdirectory
     */
    public JsonSalvageTierRepository(Path dataRoot) {
        this.objectMapper = createMapper();
        this.tierMapper = new SalvageTierMapper();
        this.tiersFilePath = Objects.requireNonNull(dataRoot, "Data root is required")
            .resolve(SALVAGE_DIR).resolve(TIERS_FILE);
    }

    @Override
    public List<SalvageTier> findAll() throws SalvageTierRepositoryException {
        List<SalvageTier> loaded = cache;
        if (loaded == null) {
            loaded = load();
            cache = loaded;
        }
        return loaded;
    }

    private List<SalvageTier> load() throws SalvageTierRepositoryException {
        if (!Files.exists(tiersFilePath)) {
            throw new SalvageTierRepositoryException(
                "Salvage tier file not found at " + tiersFilePath);
        }
        SalvageTiersDto dto = readDto();
        if (dto.schemaVersion() != SCHEMA_VERSION) {
            throw new SalvageTierRepositoryException(
                "Unsupported salvage tier schema version " + dto.schemaVersion() + " in " + tiersFilePath);
        }
        List<SalvageTierDto> tierDtos =
            Objects.requireNonNullElse(dto.tiers(), List.<SalvageTierDto>of());
        List<SalvageTier> tiers = new ArrayList<>();
        try {
            for (SalvageTierDto tierDto : tierDtos) {
                tiers.add(tierMapper.toDomain(tierDto));
            }
        } catch (IllegalArgumentException e) {
            throw new SalvageTierRepositoryException(
                "Invalid salvage tier data in " + tiersFilePath + ": " + e.getMessage(), e);
        }
        log.info("Loaded {} salvage tier(s) from {}", tiers.size(), tiersFilePath);
        return List.copyOf(tiers);
    }

    private SalvageTiersDto readDto() throws SalvageTierRepositoryException {
        try {
            return objectMapper.readValue(tiersFilePath.toFile(), SalvageTiersDto.class);
        } catch (IOException e) {
            throw new SalvageTierRepositoryException(
                "Failed to read salvage tier data from " + tiersFilePath + ": " + e.getMessage(), e);
        }
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
