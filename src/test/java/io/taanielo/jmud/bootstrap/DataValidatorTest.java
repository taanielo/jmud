package io.taanielo.jmud.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void cleanData_reportsSuccess() throws IOException {
        Path dataRoot = tempDir.resolve("data");
        Path playersRoot = tempDir.resolve("players");
        Files.createDirectories(dataRoot.resolve("rooms"));
        Files.createDirectories(playersRoot);

        // Valid JSON files
        Files.writeString(dataRoot.resolve("rooms/room1.json"), "{\"id\": \"town-square\", \"name\": \"Town Square\"}");
        Files.writeString(dataRoot.resolve("rooms/room2.json"), "[1, 2, 3]");
        Files.writeString(playersRoot.resolve("hero.json"), "{\"username\": \"hero\"}");

        DataValidator validator = new DataValidator();
        DataValidator.ValidationReport report = validator.validate(dataRoot, playersRoot);

        assertTrue(report.clean(), "Expected a clean report but got errors: " + report);
        assertEquals(0, report.totalErrors());

        // rooms domain should have 2 files; players should have 1
        DataValidator.DomainResult rooms = domainResult(report, "rooms");
        assertEquals(2, rooms.fileCount());
        assertTrue(rooms.clean());

        DataValidator.DomainResult players = domainResult(report, "players");
        assertEquals(1, players.fileCount());
        assertTrue(players.clean());
    }

    @Test
    void brokenJson_reportedInErrors() throws IOException {
        Path dataRoot = tempDir.resolve("data");
        Path playersRoot = tempDir.resolve("players");
        Files.createDirectories(dataRoot.resolve("items"));
        Files.createDirectories(playersRoot);

        // One valid file, one broken file
        Files.writeString(dataRoot.resolve("items/sword.json"), "{\"id\": \"sword\"}");
        Path brokenFile = dataRoot.resolve("items/axe.json");
        Files.writeString(brokenFile, "{ this is not valid JSON !!!");

        DataValidator validator = new DataValidator();
        DataValidator.ValidationReport report = validator.validate(dataRoot, playersRoot);

        assertFalse(report.clean(), "Expected validation to fail for broken JSON");
        assertEquals(1, report.totalErrors());

        DataValidator.DomainResult items = domainResult(report, "items");
        assertFalse(items.clean());
        assertEquals(1, items.errors().size());
        assertTrue(items.errors().get(0).path().contains("axe.json"),
            "Error path should mention axe.json, but was: " + items.errors().get(0).path());
    }

    @Test
    void missingDirectory_skippedWithZeroFiles() {
        Path dataRoot = tempDir.resolve("data");   // does not exist
        Path playersRoot = tempDir.resolve("players"); // does not exist

        DataValidator validator = new DataValidator();
        DataValidator.ValidationReport report = validator.validate(dataRoot, playersRoot);

        assertTrue(report.clean(), "Missing directories should produce a clean report");
        assertEquals(0, report.totalFiles());
    }

    @Test
    void multipleErrors_allReported() throws IOException {
        Path dataRoot = tempDir.resolve("data");
        Path playersRoot = tempDir.resolve("players");
        Files.createDirectories(dataRoot.resolve("mobs"));
        Files.createDirectories(playersRoot);

        Files.writeString(dataRoot.resolve("mobs/mob1.json"), "{ broken");
        Files.writeString(dataRoot.resolve("mobs/mob2.json"), "also broken");
        Files.writeString(dataRoot.resolve("mobs/mob3.json"), "{\"id\": \"mob3\"}");

        DataValidator validator = new DataValidator();
        DataValidator.ValidationReport report = validator.validate(dataRoot, playersRoot);

        assertFalse(report.clean());
        assertEquals(2, report.totalErrors(), "Both broken files should be reported");

        DataValidator.DomainResult mobs = domainResult(report, "mobs");
        assertEquals(3, mobs.fileCount());
        assertEquals(2, mobs.errors().size());
    }

    private static DataValidator.DomainResult domainResult(DataValidator.ValidationReport report, String domain) {
        return report.domains().stream()
            .filter(d -> d.domain().equals(domain))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Domain not found in report: " + domain));
    }
}
