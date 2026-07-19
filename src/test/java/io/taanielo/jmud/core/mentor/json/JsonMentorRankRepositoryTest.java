package io.taanielo.jmud.core.mentor.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.mentor.MentorRankException;
import io.taanielo.jmud.core.mentor.MentorRankLadder;

/**
 * Verifies that {@link JsonMentorRankRepository} loads the seeded {@code data/mentor/ranks.json}
 * ladder, orders the rungs, and rejects malformed, unsupported, or non-conforming data.
 */
class JsonMentorRankRepositoryTest {

    @Test
    void load_loadsSeededLadderInAscendingOrder() throws MentorRankException {
        JsonMentorRankRepository repo = new JsonMentorRankRepository(Path.of("data"));

        MentorRankLadder ladder = repo.load();

        assertTrue(ladder.ranks().size() >= 2, "the shipped ladder must have several rungs");
        assertEquals(1, ladder.ranks().getFirst().menteesRequired(), "the first rung is one graduation");
        assertEquals("the Mentor", ladder.ranks().getFirst().title());
        // Perk grows with rank and never regresses as thresholds climb.
        int previousPercent = -1;
        int previousThreshold = 0;
        for (var rank : ladder.ranks()) {
            assertTrue(rank.menteesRequired() > previousThreshold, "thresholds strictly ascending");
            assertTrue(rank.mentorXpBonusPercent() >= previousPercent, "perk never regresses");
            previousThreshold = rank.menteesRequired();
            previousPercent = rank.mentorXpBonusPercent();
        }
    }

    @Test
    void load_rejectsUnsupportedSchemaVersion(@TempDir Path tempDir) throws Exception {
        writeRanks(tempDir, """
            { "schema_version": 99, "ranks": [ { "mentees_required": 1, "title": "the Mentor", "mentor_xp_bonus_percent": 5 } ] }
            """);

        JsonMentorRankRepository repo = new JsonMentorRankRepository(tempDir);

        MentorRankException ex = assertThrows(MentorRankException.class, repo::load);
        assertTrue(ex.getMessage().contains("schema version"), ex.getMessage());
    }

    @Test
    void load_rejectsMissingFile(@TempDir Path tempDir) {
        JsonMentorRankRepository repo = new JsonMentorRankRepository(tempDir);

        MentorRankException ex = assertThrows(MentorRankException.class, repo::load);
        assertTrue(ex.getMessage().contains("not found"), ex.getMessage());
    }

    @Test
    void load_rejectsLadderWhoseLowestRungIsNotOne(@TempDir Path tempDir) throws Exception {
        writeRanks(tempDir, """
            { "schema_version": 1, "ranks": [ { "mentees_required": 2, "title": "the Mentor", "mentor_xp_bonus_percent": 5 } ] }
            """);

        JsonMentorRankRepository repo = new JsonMentorRankRepository(tempDir);

        MentorRankException ex = assertThrows(MentorRankException.class, repo::load);
        assertTrue(ex.getMessage().contains("one graduated mentee"), ex.getMessage());
    }

    @Test
    void load_rejectsEmptyLadder(@TempDir Path tempDir) throws Exception {
        writeRanks(tempDir, """
            { "schema_version": 1, "ranks": [] }
            """);

        JsonMentorRankRepository repo = new JsonMentorRankRepository(tempDir);

        MentorRankException ex = assertThrows(MentorRankException.class, repo::load);
        assertTrue(ex.getMessage().contains("no ranks"), ex.getMessage());
    }

    private static void writeRanks(Path dir, String json) throws Exception {
        Path mentorDir = dir.resolve("mentor");
        Files.createDirectories(mentorDir);
        Files.writeString(mentorDir.resolve("ranks.json"), json);
    }
}
