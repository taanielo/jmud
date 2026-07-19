package io.taanielo.jmud.core.achievement.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.achievement.Achievement;
import io.taanielo.jmud.core.achievement.AchievementCondition;
import io.taanielo.jmud.core.achievement.AchievementId;
import io.taanielo.jmud.core.achievement.AchievementRepositoryException;

class JsonAchievementRepositoryTest {

    @Test
    void loadsKillAchievementDefinition(@TempDir Path dataRoot) throws IOException, AchievementRepositoryException {
        Path dir = Files.createDirectories(dataRoot.resolve("achievements"));
        Files.writeString(dir.resolve("first_kill.json"), """
            {
              "schema_version": 1,
              "id": "first_kill",
              "name": "First Blood",
              "description": "Slay your first enemy.",
              "condition": "total_kills",
              "threshold": 1
            }
            """);

        JsonAchievementRepository repository = new JsonAchievementRepository(dataRoot);
        Achievement achievement = repository.findById(AchievementId.of("first_kill")).orElseThrow();

        assertEquals("First Blood", achievement.name());
        assertEquals(AchievementCondition.TOTAL_KILLS, achievement.condition());
        assertEquals(1, achievement.threshold());
    }

    @Test
    void loadsLevelAchievementDefinition(@TempDir Path dataRoot) throws IOException, AchievementRepositoryException {
        Path dir = Files.createDirectories(dataRoot.resolve("achievements"));
        Files.writeString(dir.resolve("level_10.json"), """
            {
              "schema_version": 1,
              "id": "level_10",
              "name": "Seasoned Adventurer",
              "description": "Reach character level 10.",
              "condition": "level",
              "threshold": 10
            }
            """);

        JsonAchievementRepository repository = new JsonAchievementRepository(dataRoot);
        Achievement achievement = repository.findById(AchievementId.of("level_10")).orElseThrow();

        assertEquals(AchievementCondition.LEVEL, achievement.condition());
        assertEquals(10, achievement.threshold());
    }

    @Test
    void parsesOptionalTitleReward(@TempDir Path dataRoot) throws IOException, AchievementRepositoryException {
        Path dir = Files.createDirectories(dataRoot.resolve("achievements"));
        Files.writeString(dir.resolve("first_kill.json"), """
            {
              "schema_version": 1,
              "id": "first_kill",
              "name": "First Blood",
              "description": "Slay your first enemy.",
              "condition": "total_kills",
              "threshold": 1,
              "title_reward": "the Blooded"
            }
            """);

        JsonAchievementRepository repository = new JsonAchievementRepository(dataRoot);
        Achievement achievement = repository.findById(AchievementId.of("first_kill")).orElseThrow();

        assertEquals("the Blooded", achievement.titleReward());
    }

    @Test
    void titleRewardIsNullWhenAbsent(@TempDir Path dataRoot) throws IOException, AchievementRepositoryException {
        Path dir = Files.createDirectories(dataRoot.resolve("achievements"));
        Files.writeString(dir.resolve("level_10.json"), """
            {
              "schema_version": 1,
              "id": "level_10",
              "name": "Seasoned Adventurer",
              "description": "Reach character level 10.",
              "condition": "level",
              "threshold": 10
            }
            """);

        JsonAchievementRepository repository = new JsonAchievementRepository(dataRoot);
        Achievement achievement = repository.findById(AchievementId.of("level_10")).orElseThrow();

        assertTrue(achievement.titleReward() == null);
    }

    @Test
    void rejectsUnsupportedSchemaVersion(@TempDir Path dataRoot) throws IOException {
        Path dir = Files.createDirectories(dataRoot.resolve("achievements"));
        Files.writeString(dir.resolve("bad.json"), """
            {
              "schema_version": 99,
              "id": "bad",
              "name": "Bad",
              "condition": "level",
              "threshold": 5
            }
            """);

        assertThrows(AchievementRepositoryException.class, () -> new JsonAchievementRepository(dataRoot));
    }

    @Test
    void emptyDirectoryLoadsNoAchievements(@TempDir Path dataRoot) throws AchievementRepositoryException {
        JsonAchievementRepository repository = new JsonAchievementRepository(dataRoot);
        assertTrue(repository.findAll().isEmpty());
    }
}
