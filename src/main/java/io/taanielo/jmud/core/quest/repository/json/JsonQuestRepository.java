package io.taanielo.jmud.core.quest.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestTemplate;

/**
 * Loads {@link QuestTemplate} definitions from {@code data/quests/quest.*.json} files.
 *
 * <p>All quests are eagerly loaded and cached at construction time.
 */
@Slf4j
public class JsonQuestRepository implements QuestRepository {

    private static final int SCHEMA_VERSION_KILL = 1;
    private static final int SCHEMA_VERSION_DELIVERY = 2;
    private static final int SCHEMA_VERSION_NPC_DELIVERY = 3;
    private static final int SCHEMA_VERSION_EXPLORATION = 4;
    private static final String QUESTS_DIR = "quests";

    private final ObjectMapper objectMapper;
    private final Path questsDirPath;
    private List<QuestTemplate> cache;

    public JsonQuestRepository() throws QuestRepositoryException {
        this(Path.of("data"));
    }

    public JsonQuestRepository(Path dataRoot) throws QuestRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.questsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(QUESTS_DIR);
        ensureDirectory(questsDirPath);
    }

    @Override
    public List<QuestTemplate> findAll() throws QuestRepositoryException {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    @Override
    public Optional<QuestTemplate> findById(QuestId id) throws QuestRepositoryException {
        Objects.requireNonNull(id, "id is required");
        return findAll().stream()
            .filter(q -> q.id().equals(id))
            .findFirst();
    }

    // ── private helpers ───────────────────────────────────────────────

    private List<QuestTemplate> load() throws QuestRepositoryException {
        List<QuestTemplate> quests = new ArrayList<>();
        try (var stream = Files.list(questsDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                QuestDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION_KILL
                        && dto.schemaVersion() != SCHEMA_VERSION_DELIVERY
                        && dto.schemaVersion() != SCHEMA_VERSION_NPC_DELIVERY
                        && dto.schemaVersion() != SCHEMA_VERSION_EXPLORATION) {
                    throw new QuestRepositoryException(
                        "Unsupported quest schema version " + dto.schemaVersion() + " in " + path);
                }
                quests.add(toDomain(dto, path));
            }
        } catch (IOException e) {
            throw new QuestRepositoryException("Failed to list quest data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} quest(s) from {}", quests.size(), questsDirPath);
        return List.copyOf(quests);
    }

    private QuestTemplate toDomain(QuestDto dto, Path source) throws QuestRepositoryException {
        try {
            return new QuestTemplate(
                QuestId.of(dto.id()),
                dto.name(),
                dto.description(),
                dto.targetMobId(),
                dto.requiredKills(),
                dto.goldReward(),
                dto.xpReward(),
                dto.dropItemId(),
                dto.requiredDropCount(),
                dto.titleReward(),
                dto.giverNpcId(),
                dto.receiverNpcId(),
                dto.receiverRoomId(),
                dto.packageItemId(),
                dto.requiredRoomIds()
            );
        } catch (IllegalArgumentException e) {
            throw new QuestRepositoryException("Invalid quest data in " + source + ": " + e.getMessage(), e);
        }
    }

    private QuestDto readDto(Path path) throws QuestRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), QuestDto.class);
        } catch (IOException e) {
            throw new QuestRepositoryException(
                "Failed to read quest data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws QuestRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new QuestRepositoryException("Failed to create quests directory " + path, e);
        }
    }
}
