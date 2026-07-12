package io.taanielo.jmud.core.creation.json;

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

import io.taanielo.jmud.core.creation.NewbieKit;
import io.taanielo.jmud.core.creation.NewbieKitException;
import io.taanielo.jmud.core.creation.NewbieKitRepository;
import io.taanielo.jmud.core.creation.json.NewbieKitDto.NewbieKitItemDto;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Loads the {@link NewbieKit} definition from {@code data/newbie-kit.json}.
 *
 * <p>The single file is eagerly loaded and cached on first access, so no blocking I/O reaches the
 * tick loop (AGENTS.md §5). Item quantities are flattened into repeated {@link ItemId} entries so
 * the domain kit is a simple list of items to add to a new character's inventory.
 */
@Slf4j
public class JsonNewbieKitRepository implements NewbieKitRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String KIT_FILE = "newbie-kit.json";

    private final ObjectMapper objectMapper;
    private final Path kitFilePath;
    @Nullable
    private NewbieKit cache;

    /** Creates a repository loading the kit file from the default {@code data} directory. */
    public JsonNewbieKitRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository loading the kit file from a specific data root, allowing tests to point at
     * a fixture directory.
     *
     * @param dataRoot the data root directory containing {@code newbie-kit.json}
     */
    public JsonNewbieKitRepository(Path dataRoot) {
        this.objectMapper = createMapper();
        this.kitFilePath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(KIT_FILE);
    }

    @Override
    public NewbieKit load() throws NewbieKitException {
        NewbieKit loaded = cache;
        if (loaded == null) {
            loaded = readAndMap();
            cache = loaded;
        }
        return loaded;
    }

    private NewbieKit readAndMap() throws NewbieKitException {
        if (!Files.exists(kitFilePath)) {
            throw new NewbieKitException("Newbie kit file not found at " + kitFilePath);
        }
        NewbieKitDto dto = readDto();
        if (dto.schemaVersion() != SCHEMA_VERSION) {
            throw new NewbieKitException(
                "Unsupported newbie kit schema version " + dto.schemaVersion() + " in " + kitFilePath);
        }
        if (dto.startingGold() < 0) {
            throw new NewbieKitException(
                "Newbie kit starting_gold must not be negative in " + kitFilePath);
        }
        List<NewbieKitItemDto> itemDtos =
            Objects.requireNonNullElse(dto.startingItems(), List.<NewbieKitItemDto>of());
        List<ItemId> itemIds = new ArrayList<>();
        for (NewbieKitItemDto itemDto : itemDtos) {
            String id = itemDto.item();
            if (id == null || id.isBlank()) {
                throw new NewbieKitException("Newbie kit contains an item entry without an id in " + kitFilePath);
            }
            int quantity = itemDto.quantity() == null ? 1 : itemDto.quantity();
            if (quantity < 1) {
                throw new NewbieKitException(
                    "Newbie kit item '" + id + "' has a non-positive quantity in " + kitFilePath);
            }
            for (int i = 0; i < quantity; i++) {
                itemIds.add(ItemId.of(id));
            }
        }
        NewbieKit kit = new NewbieKit(dto.startingGold(), itemIds);
        log.info("Loaded newbie kit ({} gold, {} item(s)) from {}",
            kit.startingGold(), kit.itemIds().size(), kitFilePath);
        return kit;
    }

    private NewbieKitDto readDto() throws NewbieKitException {
        try {
            return objectMapper.readValue(kitFilePath.toFile(), NewbieKitDto.class);
        } catch (IOException e) {
            throw new NewbieKitException(
                "Failed to read newbie kit data from " + kitFilePath + ": " + e.getMessage(), e);
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
