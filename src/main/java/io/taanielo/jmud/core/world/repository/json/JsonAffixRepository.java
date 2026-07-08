package io.taanielo.jmud.core.world.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.world.AffixId;
import io.taanielo.jmud.core.world.ItemAffix;
import io.taanielo.jmud.core.world.dto.AffixDto;
import io.taanielo.jmud.core.world.dto.AffixFileDto;
import io.taanielo.jmud.core.world.dto.AffixMapper;
import io.taanielo.jmud.core.world.repository.AffixRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Loads stat affix definitions from the single {@code data/item-affixes.json} file. The file is
 * read and cached on first access; a missing file yields an empty affix set so items without
 * affixes continue to load unchanged.
 */
public class JsonAffixRepository implements AffixRepository {

    /** Supported schema version of {@code data/item-affixes.json}. */
    static final int AFFIX_SCHEMA_VERSION = 1;

    private static final String AFFIXES_FILE = "item-affixes.json";

    private final ObjectMapper objectMapper;
    private final AffixMapper mapper;
    private final Path affixesFilePath;

    private Map<AffixId, ItemAffix> cache;

    public JsonAffixRepository() {
        this(Path.of("data"));
    }

    public JsonAffixRepository(Path dataRoot) {
        this.objectMapper = JsonDataMapper.create();
        this.mapper = new AffixMapper();
        this.affixesFilePath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(AFFIXES_FILE);
    }

    @Override
    public Optional<ItemAffix> findById(AffixId id) throws RepositoryException {
        Objects.requireNonNull(id, "Affix id is required");
        return Optional.ofNullable(load().get(id));
    }

    @Override
    public List<ItemAffix> findAll() throws RepositoryException {
        return new ArrayList<>(load().values());
    }

    private synchronized Map<AffixId, ItemAffix> load() throws RepositoryException {
        if (cache != null) {
            return cache;
        }
        if (!Files.exists(affixesFilePath)) {
            cache = Map.of();
            return cache;
        }
        AffixFileDto fileDto = readFile();
        if (fileDto.schemaVersion() != AFFIX_SCHEMA_VERSION) {
            throw new RepositoryException(
                "Unsupported affix schema version " + fileDto.schemaVersion() + " in " + affixesFilePath);
        }
        Map<AffixId, ItemAffix> loaded = new LinkedHashMap<>();
        List<AffixDto> affixes = fileDto.affixes() == null ? List.of() : fileDto.affixes();
        for (AffixDto dto : affixes) {
            ItemAffix affix;
            try {
                affix = mapper.toDomain(dto);
            } catch (IllegalArgumentException e) {
                throw new RepositoryException(
                    "Invalid affix data in " + affixesFilePath + ": " + e.getMessage(), e);
            }
            if (loaded.put(affix.id(), affix) != null) {
                throw new RepositoryException(
                    "Duplicate affix id '" + affix.id().getValue() + "' in " + affixesFilePath);
            }
        }
        cache = Map.copyOf(loaded);
        return cache;
    }

    private AffixFileDto readFile() throws RepositoryException {
        try {
            return objectMapper.readValue(affixesFilePath.toFile(), AffixFileDto.class);
        } catch (IOException e) {
            throw new RepositoryException(
                "Failed to read affix data from " + affixesFilePath + ": " + e.getMessage(), e);
        }
    }
}
