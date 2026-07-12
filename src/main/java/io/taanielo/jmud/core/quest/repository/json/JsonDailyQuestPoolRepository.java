package io.taanielo.jmud.core.quest.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.quest.DailyQuestPool;
import io.taanielo.jmud.core.quest.DailyQuestPoolRepository;
import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestTemplate;

/**
 * Loads {@link DailyQuestPool} definitions from {@code data/quests/daily/*.json} files (schema
 * version 5).
 *
 * <p>All pools are eagerly loaded and cached at construction-time query. Each file describes one
 * pool of rotating kill-quest variants.
 */
@Slf4j
public class JsonDailyQuestPoolRepository implements DailyQuestPoolRepository {

    private static final int SCHEMA_VERSION_DAILY = 5;
    private static final String DAILY_DIR = "daily";
    private static final String QUESTS_DIR = "quests";

    private final ObjectMapper objectMapper;
    private final Path dailyDirPath;
    private List<DailyQuestPool> cache;

    public JsonDailyQuestPoolRepository() throws QuestRepositoryException {
        this(Path.of("data"));
    }

    public JsonDailyQuestPoolRepository(Path dataRoot) throws QuestRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.dailyDirPath = Objects.requireNonNull(dataRoot, "Data root is required")
            .resolve(QUESTS_DIR).resolve(DAILY_DIR);
        ensureDirectory(dailyDirPath);
    }

    @Override
    public List<DailyQuestPool> findAll() throws QuestRepositoryException {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    // ── private helpers ───────────────────────────────────────────────

    private List<DailyQuestPool> load() throws QuestRepositoryException {
        List<DailyQuestPool> pools = new ArrayList<>();
        try (var stream = Files.list(dailyDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                DailyQuestPoolDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION_DAILY) {
                    throw new QuestRepositoryException(
                        "Unsupported daily quest schema version " + dto.schemaVersion() + " in " + path);
                }
                pools.add(toDomain(dto, path));
            }
        } catch (IOException e) {
            throw new QuestRepositoryException("Failed to list daily quest data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} daily quest pool(s) from {}", pools.size(), dailyDirPath);
        return List.copyOf(pools);
    }

    private DailyQuestPool toDomain(DailyQuestPoolDto dto, Path source) throws QuestRepositoryException {
        if (dto.quests() == null || dto.quests().isEmpty()) {
            throw new QuestRepositoryException("Daily quest pool has no variants in " + source);
        }
        try {
            List<QuestTemplate> variants = new ArrayList<>(dto.quests().size());
            for (DailyQuestPoolDto.DailyQuestVariantDto variant : dto.quests()) {
                int itemRewardQuantity = variant.itemReward() != null && variant.itemRewardQuantity() <= 0
                    ? 1
                    : variant.itemRewardQuantity();
                variants.add(new QuestTemplate(
                    QuestId.of(variant.id()),
                    variant.name(),
                    variant.description(),
                    variant.targetMobId(),
                    variant.requiredKills(),
                    variant.goldReward(),
                    variant.xpReward(),
                    null,
                    0,
                    variant.titleReward(),
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    dto.poolId(),
                    variant.itemReward(),
                    itemRewardQuantity,
                    variant.reputationRewardFactionId(),
                    variant.reputationRewardDelta(),
                    true,
                    null,
                    0
                ));
            }
            return new DailyQuestPool(dto.poolId(), dto.name(), variants);
        } catch (IllegalArgumentException e) {
            throw new QuestRepositoryException("Invalid daily quest data in " + source + ": " + e.getMessage(), e);
        }
    }

    private DailyQuestPoolDto readDto(Path path) throws QuestRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), DailyQuestPoolDto.class);
        } catch (IOException e) {
            throw new QuestRepositoryException(
                "Failed to read daily quest data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws QuestRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new QuestRepositoryException("Failed to create daily quests directory " + path, e);
        }
    }
}
