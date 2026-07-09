package io.taanielo.jmud.core.dialogue.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.dialogue.DialogueId;
import io.taanielo.jmud.core.dialogue.DialogueRepositoryException;
import io.taanielo.jmud.core.dialogue.DialogueTree;

/**
 * Unit tests for {@link JsonDialogueRepository}: loading valid trees, schema-version enforcement,
 * and validation of undefined response targets. No networking required (AGENTS.md §10).
 */
class JsonDialogueRepositoryTest {

    private static final String VALID = """
        {
          "schema_version": 1,
          "id": "borin-blacksmith-welcome",
          "npc_id": "blacksmith",
          "start_node": "greeting",
          "nodes": {
            "greeting": {
              "text": "Aye?",
              "responses": [
                { "text": "Repair my gear?", "target": "repair" },
                { "text": "Bye.", "target": "end" }
              ]
            },
            "repair": { "text": "Bring it here.", "responses": [ { "text": "Ok.", "target": "end" } ] },
            "end": { "text": "Safe travels.", "responses": [] }
          }
        }
        """;

    private void write(Path dir, String name, String content) throws IOException {
        Files.createDirectories(dir.resolve("dialogues"));
        Files.writeString(dir.resolve("dialogues").resolve(name), content);
    }

    @Test
    void shippedBlacksmithDialogueLoadsFromDataDirectory() throws Exception {
        JsonDialogueRepository repo = new JsonDialogueRepository(Path.of("data"));
        DialogueTree tree = repo.findById(DialogueId.of("borin-blacksmith-welcome")).orElseThrow();
        assertEquals("blacksmith", tree.npcId());
        assertTrue(tree.node(tree.startNodeId()).isPresent());
    }

    @Test
    void loadsValidDialogueTree(@TempDir Path dir) throws Exception {
        write(dir, "borin.json", VALID);
        JsonDialogueRepository repo = new JsonDialogueRepository(dir);

        DialogueTree tree = repo.findById(DialogueId.of("borin-blacksmith-welcome")).orElseThrow();
        assertEquals("blacksmith", tree.npcId());
        assertEquals("greeting", tree.startNodeId());
        assertEquals(3, tree.nodes().size());
        assertEquals(2, tree.startNode().responses().size());
        assertTrue(tree.node("end").orElseThrow().isTerminal());
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void emptyDirectoryLoadsNoTrees(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("dialogues"));
        JsonDialogueRepository repo = new JsonDialogueRepository(dir);
        assertTrue(repo.findAll().isEmpty());
        assertTrue(repo.findById(DialogueId.of("missing")).isEmpty());
    }

    @Test
    void unsupportedSchemaVersionIsRejected(@TempDir Path dir) throws Exception {
        write(dir, "bad.json", VALID.replace("\"schema_version\": 1", "\"schema_version\": 99"));
        assertThrows(DialogueRepositoryException.class, () -> new JsonDialogueRepository(dir));
    }

    @Test
    void loadsSchemaVersion2WithGrantQuestId(@TempDir Path dir) throws Exception {
        String v2 = """
            {
              "schema_version": 2,
              "id": "quartermaster-errand",
              "npc_id": "quartermaster",
              "start_node": "greeting",
              "nodes": {
                "greeting": {
                  "text": "Carry this?",
                  "responses": [
                    { "text": "Yes.", "target": "accepted", "grant_quest_id": "deliver-package" },
                    { "text": "No.", "target": "accepted" }
                  ]
                },
                "accepted": { "text": "Good.", "responses": [] }
              }
            }
            """;
        write(dir, "quartermaster.json", v2);
        JsonDialogueRepository repo = new JsonDialogueRepository(dir);

        DialogueTree tree = repo.findById(DialogueId.of("quartermaster-errand")).orElseThrow();
        assertEquals("deliver-package", tree.startNode().responses().get(0).grantQuestId());
        assertNull(tree.startNode().responses().get(1).grantQuestId());
    }

    @Test
    void undefinedTargetNodeIsRejected(@TempDir Path dir) throws Exception {
        String broken = """
            {
              "schema_version": 1,
              "id": "broken",
              "npc_id": "blacksmith",
              "start_node": "greeting",
              "nodes": {
                "greeting": {
                  "text": "Aye?",
                  "responses": [ { "text": "Go", "target": "nowhere" } ]
                }
              }
            }
            """;
        write(dir, "broken.json", broken);
        assertThrows(DialogueRepositoryException.class, () -> new JsonDialogueRepository(dir));
    }

    @Test
    void undefinedStartNodeIsRejected(@TempDir Path dir) throws Exception {
        String broken = """
            {
              "schema_version": 1,
              "id": "broken-start",
              "npc_id": "blacksmith",
              "start_node": "missing",
              "nodes": {
                "greeting": { "text": "Aye?", "responses": [] }
              }
            }
            """;
        write(dir, "broken.json", broken);
        assertThrows(DialogueRepositoryException.class, () -> new JsonDialogueRepository(dir));
    }
}
