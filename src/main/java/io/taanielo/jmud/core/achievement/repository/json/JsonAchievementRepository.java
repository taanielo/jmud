package io.taanielo.jmud.core.achievement.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.achievement.Achievement;
import io.taanielo.jmud.core.achievement.AchievementCondition;
import io.taanielo.jmud.core.achievement.AchievementId;
import io.taanielo.jmud.core.achievement.AchievementRepository;
import io.taanielo.jmud.core.achievement.AchievementRepositoryException;

/**
 * Loads {@link Achievement} definitions from {@code data/achievements/*.json} files.
 *
 * <p>All achievements are eagerly loaded and cached at construction time so lookups never touch disk
 * on the tick thread (AGENTS.md §5).
 */
@Slf4j
public class JsonAchievementRepository implements AchievementRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String ACHIEVEMENTS_DIR = "achievements";

    private final ObjectMapper objectMapper;
    private final Path achievementsDirPath;
    private final List<Achievement> cache;

    public JsonAchievementRepository() throws AchievementRepositoryException {
        this(Path.of("data"));
    }

    public JsonAchievementRepository(Path dataRoot) throws AchievementRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.achievementsDirPath =
            Objects.requireNonNull(dataRoot, "Data root is required").resolve(ACHIEVEMENTS_DIR);
        ensureDirectory(achievementsDirPath);
        this.cache = load();
    }

    @Override
    public List<Achievement> findAll() {
        return cache;
    }

    @Override
    public Optional<Achievement> findById(AchievementId id) {
        Objects.requireNonNull(id, "id is required");
        return cache.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    // ── private helpers ───────────────────────────────────────────────

    private List<Achievement> load() throws AchievementRepositoryException {
        List<Achievement> achievements = new ArrayList<>();
        try (var stream = Files.list(achievementsDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                AchievementDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new AchievementRepositoryException(
                        "Unsupported achievement schema version " + dto.schemaVersion() + " in " + path);
                }
                achievements.add(toDomain(dto, path));
            }
        } catch (IOException e) {
            throw new AchievementRepositoryException(
                "Failed to list achievement data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} achievement(s) from {}", achievements.size(), achievementsDirPath);
        return List.copyOf(achievements);
    }

    private Achievement toDomain(AchievementDto dto, Path source) throws AchievementRepositoryException {
        try {
            AchievementCondition condition = AchievementCondition.fromToken(
                Objects.requireNonNull(dto.condition(), "Achievement condition is required"));
            return new Achievement(
                AchievementId.of(Objects.requireNonNull(dto.id(), "Achievement id is required")),
                Objects.requireNonNull(dto.name(), "Achievement name is required"),
                dto.description() != null ? dto.description() : "",
                condition,
                Objects.requireNonNull(dto.threshold(), "Achievement threshold is required"),
                dto.titleReward()
            );
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AchievementRepositoryException(
                "Invalid achievement data in " + source + ": " + e.getMessage(), e);
        }
    }

    private AchievementDto readDto(Path path) throws AchievementRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), AchievementDto.class);
        } catch (IOException e) {
            throw new AchievementRepositoryException(
                "Failed to read achievement data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws AchievementRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new AchievementRepositoryException(
                "Failed to create achievements directory " + path, e);
        }
    }
}
