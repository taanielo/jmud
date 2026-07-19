package io.taanielo.jmud.core.guild.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.guild.GuildQuestObjective;
import io.taanielo.jmud.core.guild.GuildQuestPool;
import io.taanielo.jmud.core.guild.GuildQuestPoolRepository;
import io.taanielo.jmud.core.guild.GuildRepositoryException;

/**
 * Loads the {@link GuildQuestPool} from {@code data/quests/guild/*.json} files (schema version 1).
 *
 * <p>Every file's objectives are concatenated, in filename order, into a single level-banded pool. The
 * pool is eagerly assembled on {@link #load()}; the data is static game content (AGENTS.md §11).
 */
@Slf4j
public class JsonGuildQuestPoolRepository implements GuildQuestPoolRepository {

    private static final int SCHEMA_VERSION_GUILD = 1;
    private static final String GUILD_DIR = "guild";
    private static final String QUESTS_DIR = "quests";

    private final ObjectMapper objectMapper;
    private final Path guildQuestDirPath;

    public JsonGuildQuestPoolRepository() throws GuildRepositoryException {
        this(Path.of("data"));
    }

    public JsonGuildQuestPoolRepository(Path dataRoot) throws GuildRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.guildQuestDirPath = Objects.requireNonNull(dataRoot, "Data root is required")
            .resolve(QUESTS_DIR).resolve(GUILD_DIR);
        ensureDirectory(guildQuestDirPath);
    }

    @Override
    public GuildQuestPool load() throws GuildRepositoryException {
        List<GuildQuestObjective> objectives = new ArrayList<>();
        try (var stream = Files.list(guildQuestDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                GuildQuestPoolDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION_GUILD) {
                    throw new GuildRepositoryException(
                        "Unsupported guild quest schema version " + dto.schemaVersion() + " in " + path);
                }
                objectives.addAll(toObjectives(dto, path));
            }
        } catch (IOException e) {
            throw new GuildRepositoryException("Failed to list guild quest data files: " + e.getMessage(), e);
        }
        if (objectives.isEmpty()) {
            throw new GuildRepositoryException(
                "No guild quest objectives found in " + guildQuestDirPath);
        }
        log.info("Loaded {} guild quest objective(s) from {}", objectives.size(), guildQuestDirPath);
        return new GuildQuestPool(objectives);
    }

    private List<GuildQuestObjective> toObjectives(GuildQuestPoolDto dto, Path source)
            throws GuildRepositoryException {
        @Nullable List<GuildQuestPoolDto.GuildQuestObjectiveDto> declared = dto.objectives();
        if (declared == null || declared.isEmpty()) {
            throw new GuildRepositoryException("Guild quest pool has no objectives in " + source);
        }
        try {
            List<GuildQuestObjective> result = new ArrayList<>(declared.size());
            for (GuildQuestPoolDto.GuildQuestObjectiveDto o : declared) {
                String id = Objects.requireNonNull(o.id(), "Guild quest objective id is required");
                String targetMobId =
                    Objects.requireNonNull(o.targetMobId(), "Guild quest target_mob_id is required");
                String name = o.name() == null ? id : o.name();
                String targetName = o.targetName() == null ? targetMobId : o.targetName();
                int minGuildLevel = o.minGuildLevel() <= 0 ? 1 : o.minGuildLevel();
                result.add(new GuildQuestObjective(
                    id, name, targetMobId, targetName,
                    o.requiredKills(), o.goldReward(), minGuildLevel));
            }
            return result;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new GuildRepositoryException(
                "Invalid guild quest data in " + source + ": " + e.getMessage(), e);
        }
    }

    private GuildQuestPoolDto readDto(Path path) throws GuildRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), GuildQuestPoolDto.class);
        } catch (IOException e) {
            throw new GuildRepositoryException(
                "Failed to read guild quest data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws GuildRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new GuildRepositoryException("Failed to create guild quests directory " + path, e);
        }
    }
}
