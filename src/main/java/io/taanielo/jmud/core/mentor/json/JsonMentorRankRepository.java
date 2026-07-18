package io.taanielo.jmud.core.mentor.json;

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

import io.taanielo.jmud.core.mentor.MentorRank;
import io.taanielo.jmud.core.mentor.MentorRankException;
import io.taanielo.jmud.core.mentor.MentorRankLadder;
import io.taanielo.jmud.core.mentor.MentorRankRepository;
import io.taanielo.jmud.core.mentor.json.MentorRankFileDto.MentorRankDto;

/**
 * Loads the Mentors' Guild {@link MentorRankLadder} from {@code data/mentor/ranks.json}.
 *
 * <p>The single file is eagerly loaded and cached on first access, so no blocking I/O reaches the
 * tick loop (AGENTS.md §5). The ladder must contain at least one rank, and its lowest rung must be
 * granted at exactly one graduated mentee so a mentor is recognised the moment their first mentee
 * graduates.
 */
@Slf4j
public class JsonMentorRankRepository implements MentorRankRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String RANKS_FILE = "mentor/ranks.json";

    private final ObjectMapper objectMapper;
    private final Path ranksFilePath;
    @Nullable
    private MentorRankLadder cache;

    /** Creates a repository loading the ranks file from the default {@code data} directory. */
    public JsonMentorRankRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository loading the ranks file from a specific data root, allowing tests to point
     * at a fixture directory.
     *
     * @param dataRoot the data root directory containing {@code mentor/ranks.json}
     */
    public JsonMentorRankRepository(Path dataRoot) {
        this.objectMapper = createMapper();
        this.ranksFilePath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(RANKS_FILE);
    }

    @Override
    public MentorRankLadder load() throws MentorRankException {
        MentorRankLadder loaded = cache;
        if (loaded == null) {
            loaded = readAndMap();
            cache = loaded;
        }
        return loaded;
    }

    private MentorRankLadder readAndMap() throws MentorRankException {
        if (!Files.exists(ranksFilePath)) {
            throw new MentorRankException("Mentor ranks file not found at " + ranksFilePath);
        }
        MentorRankFileDto dto = readDto();
        if (dto.schemaVersion() != SCHEMA_VERSION) {
            throw new MentorRankException(
                "Unsupported mentor ranks schema version " + dto.schemaVersion() + " in " + ranksFilePath);
        }
        List<MentorRankDto> rankDtos = dto.ranks();
        if (rankDtos == null || rankDtos.isEmpty()) {
            throw new MentorRankException("Mentor ranks file has no ranks in " + ranksFilePath);
        }
        List<MentorRank> ranks = new ArrayList<>(rankDtos.size());
        for (MentorRankDto rankDto : rankDtos) {
            ranks.add(toRank(rankDto));
        }
        MentorRankLadder ladder;
        try {
            ladder = new MentorRankLadder(ranks);
        } catch (IllegalArgumentException e) {
            throw new MentorRankException("Invalid mentor ranks in " + ranksFilePath + ": " + e.getMessage(), e);
        }
        int firstThreshold = ladder.ranks().getFirst().menteesRequired();
        if (firstThreshold != 1) {
            throw new MentorRankException(
                "Lowest mentor rank must be granted at exactly one graduated mentee, was "
                    + firstThreshold + " in " + ranksFilePath);
        }
        log.info("Loaded {} mentor rank(s) from {}", ladder.ranks().size(), ranksFilePath);
        return ladder;
    }

    private MentorRank toRank(MentorRankDto rankDto) throws MentorRankException {
        Integer menteesRequired = rankDto.menteesRequired();
        String title = rankDto.title();
        Integer bonusPercent = rankDto.mentorXpBonusPercent();
        if (menteesRequired == null || title == null || bonusPercent == null) {
            throw new MentorRankException(
                "Mentor rank entry is missing mentees_required, title, or mentor_xp_bonus_percent in "
                    + ranksFilePath);
        }
        try {
            return new MentorRank(menteesRequired, title, bonusPercent);
        } catch (IllegalArgumentException e) {
            throw new MentorRankException("Invalid mentor rank in " + ranksFilePath + ": " + e.getMessage(), e);
        }
    }

    private MentorRankFileDto readDto() throws MentorRankException {
        try {
            return objectMapper.readValue(ranksFilePath.toFile(), MentorRankFileDto.class);
        } catch (IOException e) {
            throw new MentorRankException(
                "Failed to read mentor ranks data from " + ranksFilePath + ": " + e.getMessage(), e);
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
