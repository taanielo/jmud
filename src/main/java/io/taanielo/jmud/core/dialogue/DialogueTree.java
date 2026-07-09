package io.taanielo.jmud.core.dialogue;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable NPC dialogue tree: a named collection of {@link DialogueNode}s reachable from a
 * {@link #startNodeId() start node}. Loaded once from JSON on start-up and never mutated.
 */
public record DialogueTree(DialogueId id, String npcId, String startNodeId, Map<String, DialogueNode> nodes) {

    public DialogueTree {
        Objects.requireNonNull(id, "Dialogue id is required");
        Objects.requireNonNull(npcId, "Npc id is required");
        Objects.requireNonNull(startNodeId, "Start node id is required");
        Objects.requireNonNull(nodes, "Nodes are required");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Dialogue tree " + id.value() + " must have at least one node");
        }
        if (!nodes.containsKey(startNodeId)) {
            throw new IllegalArgumentException(
                "Dialogue tree " + id.value() + " start node '" + startNodeId + "' is not defined");
        }
        nodes = Map.copyOf(nodes);
    }

    /**
     * Returns the node with the given id, if present.
     *
     * @param nodeId the node id to look up
     * @return the matching node, or empty when no node has that id
     */
    public Optional<DialogueNode> node(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /**
     * Returns the start node of this tree.
     *
     * @return the node identified by {@link #startNodeId()} (guaranteed present by construction)
     */
    public DialogueNode startNode() {
        DialogueNode start = nodes.get(startNodeId);
        if (start == null) {
            throw new IllegalStateException("Start node missing from dialogue tree " + id.value());
        }
        return start;
    }
}
