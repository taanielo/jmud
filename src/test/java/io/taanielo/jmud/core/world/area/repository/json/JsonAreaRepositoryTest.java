package io.taanielo.jmud.core.world.area.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.world.area.Area;
import io.taanielo.jmud.core.world.area.AreaId;
import io.taanielo.jmud.core.world.area.WorldAtlas;

class JsonAreaRepositoryTest {

    private static final String AREA_JSON =
        """
        {
          "schema_version": 1,
          "id": "town",
          "name": "Greystone Town",
          "room_ids": ["training-yard"],
          "connections": ["darkwood"],
          "level_range": { "min": 1, "max": 3 },
          "ascii_map": ["  [Town]"]
        }
        """;

    private static final String ATLAS_JSON =
        """
        {
          "schema_version": 1,
          "id": "atlas",
          "name": "The World Atlas",
          "ascii_map": ["  [World]"]
        }
        """;

    @Test
    void warmingLoadsAreasAndAtlasIntoMemory(@TempDir Path dataRoot) throws Exception {
        writeWorld(dataRoot);
        JsonAreaRepository repository = new JsonAreaRepository(dataRoot);

        // Bootstrap warm-up (mirrors GameContext.createAreaRepository, issue #552).
        List<Area> areas = repository.findAll();
        Optional<WorldAtlas> atlas = repository.findAtlas();

        assertEquals(1, areas.size(), "expected the single warmed area");
        assertEquals(AreaId.of("town"), areas.getFirst().id());
        assertTrue(atlas.isPresent(), "atlas should be warmed into memory");
        assertEquals("atlas", atlas.get().id());
    }

    @Test
    void servesFromMemoryAfterWarmingEvenWhenDataFilesDisappear(@TempDir Path dataRoot) throws Exception {
        writeWorld(dataRoot);
        JsonAreaRepository repository = new JsonAreaRepository(dataRoot);

        // Warm the cache once, as bootstrap does, so tick-thread reads never touch disk.
        repository.findAll();
        repository.findAtlas();

        // Simulate any subsequent disk unavailability: delete all data files.
        deleteWorld(dataRoot);

        // Subsequent tick-thread accesses must be served purely from the warmed cache, proving no
        // lazy directory scan / JSON parse happens on first (or any later) access.
        assertFalse(repository.findAll().isEmpty(), "findAll must serve the warmed cache, not re-read disk");
        assertTrue(repository.findById(AreaId.of("town")).isPresent(),
            "findById must serve the warmed cache, not re-read disk");
        assertTrue(repository.findAtlas().isPresent(),
            "findAtlas must serve the warmed cache, not re-read disk");
    }

    private static void writeWorld(Path dataRoot) throws IOException {
        Path areasDir = dataRoot.resolve("areas");
        Files.createDirectories(areasDir);
        Files.writeString(areasDir.resolve("town.json"), AREA_JSON);
        Files.writeString(areasDir.resolve("atlas.json"), ATLAS_JSON);
    }

    private static void deleteWorld(Path dataRoot) throws IOException {
        Path areasDir = dataRoot.resolve("areas");
        try (Stream<Path> files = Files.list(areasDir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(areasDir);
    }
}
