package io.taanielo.jmud.core.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DialogueService}: tree traversal, branching, invalid response handling, and
 * numbered-response rendering. Runs without networking (AGENTS.md §10).
 */
class DialogueServiceTest {

    private static DialogueTree sampleTree() {
        DialogueNode greeting = new DialogueNode("greeting", "Aye, what brings ye to my forge?", List.of(
            new DialogueResponse("Can you repair my gear?", "repair"),
            new DialogueResponse("Tell me about this town.", "town"),
            new DialogueResponse("I'll come back later.", "end")));
        DialogueNode repair = new DialogueNode("repair", "Aye, I can mend yer armor.", List.of(
            new DialogueResponse("Done talking.", "end")));
        DialogueNode town = new DialogueNode("town", "Quiet enough these days.", List.of(
            new DialogueResponse("About that repair...", "repair")));
        DialogueNode end = new DialogueNode("end", "Safe travels.", List.of());
        return new DialogueTree(
            DialogueId.of("borin-blacksmith-welcome"),
            "blacksmith",
            "greeting",
            Map.of("greeting", greeting, "repair", repair, "town", town, "end", end));
    }

    private final DialogueService service = new DialogueService(new StubRepository(sampleTree()));

    @Test
    void startNodeIsTheGreeting() {
        DialogueTree tree = sampleTree();
        assertEquals("greeting", tree.startNode().id());
        assertFalse(tree.startNode().isTerminal());
    }

    @Test
    void renderNodeShowsNumberedOptions() {
        DialogueTree tree = sampleTree();
        String rendered = service.renderNode("Borin the Blacksmith", tree.startNode());
        assertTrue(rendered.contains("Borin the Blacksmith says: \"Aye, what brings ye to my forge?\""));
        assertTrue(rendered.contains("You see these options:"));
        assertTrue(rendered.contains("1) Can you repair my gear?"));
        assertTrue(rendered.contains("2) Tell me about this town."));
        assertTrue(rendered.contains("3) I'll come back later."));
    }

    @Test
    void renderTerminalNodeOmitsOptions() {
        DialogueTree tree = sampleTree();
        DialogueNode end = tree.node("end").orElseThrow();
        String rendered = service.renderNode("Borin the Blacksmith", end);
        assertEquals("Borin the Blacksmith says: \"Safe travels.\"", rendered);
        assertFalse(rendered.contains("You see these options:"));
    }

    @Test
    void respondBranchesToTargetNode() {
        DialogueTree tree = sampleTree();
        DialogueNode next = service.respond(tree, "greeting", 1).orElseThrow();
        assertEquals("repair", next.id());
    }

    @Test
    void respondFollowsDifferentBranch() {
        DialogueTree tree = sampleTree();
        DialogueNode next = service.respond(tree, "greeting", 2).orElseThrow();
        assertEquals("town", next.id());
        // town -> repair -> end, verifying multi-step traversal
        DialogueNode repair = service.respond(tree, next.id(), 1).orElseThrow();
        assertEquals("repair", repair.id());
        DialogueNode end = service.respond(tree, repair.id(), 1).orElseThrow();
        assertEquals("end", end.id());
        assertTrue(end.isTerminal());
    }

    @Test
    void respondWithNumberTooLargeIsInvalid() {
        DialogueTree tree = sampleTree();
        assertTrue(service.respond(tree, "greeting", 4).isEmpty());
    }

    @Test
    void respondWithZeroOrNegativeIsInvalid() {
        DialogueTree tree = sampleTree();
        assertTrue(service.respond(tree, "greeting", 0).isEmpty());
        assertTrue(service.respond(tree, "greeting", -1).isEmpty());
    }

    @Test
    void respondFromUnknownNodeIsInvalid() {
        DialogueTree tree = sampleTree();
        assertTrue(service.respond(tree, "nonexistent", 1).isEmpty());
    }

    @Test
    void respondFromTerminalNodeIsInvalid() {
        DialogueTree tree = sampleTree();
        assertTrue(service.respond(tree, "end", 1).isEmpty());
    }

    @Test
    void selectResponseReturnsChosenResponse() {
        DialogueTree tree = sampleTree();
        DialogueResponse chosen = service.selectResponse(tree, "greeting", 1).orElseThrow();
        assertEquals("Can you repair my gear?", chosen.text());
        assertEquals("repair", chosen.target());
    }

    @Test
    void selectResponseIsEmptyForOutOfRangeNumber() {
        DialogueTree tree = sampleTree();
        assertTrue(service.selectResponse(tree, "greeting", 9).isEmpty());
        assertTrue(service.selectResponse(tree, "greeting", 0).isEmpty());
    }

    @Test
    void findTreeReturnsRegisteredTree() {
        Optional<DialogueTree> found = service.findTree(DialogueId.of("borin-blacksmith-welcome"));
        assertTrue(found.isPresent());
        assertEquals("blacksmith", found.orElseThrow().npcId());
    }

    @Test
    void findTreeReturnsEmptyForUnknownId() {
        assertTrue(service.findTree(DialogueId.of("unknown")).isEmpty());
    }

    private record StubRepository(DialogueTree tree) implements DialogueRepository {
        @Override
        public List<DialogueTree> findAll() {
            return List.of(tree);
        }

        @Override
        public Optional<DialogueTree> findById(DialogueId id) {
            return tree.id().equals(id) ? Optional.of(tree) : Optional.empty();
        }
    }
}
