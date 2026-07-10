package io.taanielo.jmud.core.gathering.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.gathering.ResourceNode;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies that {@link JsonResourceNodeRepository} loads the seeded node definitions from
 * {@code data/resource-nodes/} and rejects unsupported schema versions.
 */
class JsonResourceNodeRepositoryTest {

    @Test
    void findAll_loadsSeededNodes() throws RepositoryException {
        JsonResourceNodeRepository repo = new JsonResourceNodeRepository(Path.of("data"));

        List<ResourceNode> nodes = repo.findAll();

        assertFalse(nodes.isEmpty(), "Expected at least one resource node");
        Set<String> ids = nodes.stream().map(n -> n.id().value()).collect(Collectors.toSet());
        assertTrue(ids.contains("sewers-iron-vein"), "Expected the sewers iron vein node");
        assertTrue(ids.contains("darkwood-herb-patch"), "Expected the darkwood herb patch node");
    }

    @Test
    void findAll_returnsEmptyForEmptyDir(@TempDir Path tempDir) throws RepositoryException {
        JsonResourceNodeRepository repo = new JsonResourceNodeRepository(tempDir.resolve("missing-subdir"));

        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAll_rejectsUnsupportedSchemaVersion(@TempDir Path tempDir) throws Exception {
        Path nodesDir = tempDir.resolve("resource-nodes");
        Files.createDirectories(nodesDir);
        Files.writeString(nodesDir.resolve("bad.json"), """
            {
              "schema_version": 99,
              "id": "bad-node",
              "room_id": "courtyard",
              "yield_item": "iron-ore",
              "respawn_ticks": 5,
              "name": "bad node",
              "look_description": "A malformed node."
            }
            """);

        JsonResourceNodeRepository repo = new JsonResourceNodeRepository(tempDir);

        RepositoryException ex = assertThrows(RepositoryException.class, repo::findAll);
        assertTrue(ex.getMessage().contains("schema version"));
    }

    @Test
    void findAll_seededNodesReferenceLoadableCounts() throws RepositoryException {
        JsonResourceNodeRepository repo = new JsonResourceNodeRepository(Path.of("data"));

        List<ResourceNode> nodes = repo.findAll();

        // Every node must declare a positive respawn delay; the record enforces this on construction,
        // so a successful load already proves the seeded data is well-formed.
        assertEquals(nodes.size(), nodes.stream().filter(n -> n.respawnTicks() > 0).count());
    }
}
