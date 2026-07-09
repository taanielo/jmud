package io.taanielo.jmud.core.dialogue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain service that drives NPC dialogue: it renders a node's spoken line and numbered response
 * options for display, and resolves a player's numbered choice into the next node of the tree.
 *
 * <p>The service is stateless with respect to any individual conversation — the caller (a player
 * session) owns the current dialogue tree and node id and passes them in on each turn. Tree lookups
 * are served from pre-loaded data via the injected {@link DialogueRepository}, so no blocking I/O is
 * performed on the tick thread (AGENTS.md §5).
 */
public class DialogueService {

    private final DialogueRepository repository;

    /**
     * Creates a dialogue service backed by the given repository.
     *
     * @param repository the source of dialogue trees
     */
    public DialogueService(DialogueRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Dialogue repository is required");
    }

    /**
     * Looks up the dialogue tree with the given id.
     *
     * @param id the dialogue tree id
     * @return the tree, or empty when unknown or when the data cannot be read
     */
    public Optional<DialogueTree> findTree(DialogueId id) {
        Objects.requireNonNull(id, "Dialogue id is required");
        try {
            return repository.findById(id);
        } catch (DialogueRepositoryException e) {
            return Optional.empty();
        }
    }

    /**
     * Renders a node for display: the speaker's line, followed by the numbered response options when
     * the node is not terminal.
     *
     * @param speakerName the display name of the NPC speaking
     * @param node        the node to render
     * @return a multi-line string suitable for sending to the player
     */
    public String renderNode(String speakerName, DialogueNode node) {
        Objects.requireNonNull(speakerName, "Speaker name is required");
        Objects.requireNonNull(node, "Node is required");
        StringBuilder sb = new StringBuilder();
        sb.append(speakerName).append(" says: \"").append(node.text()).append('"');
        if (node.isTerminal()) {
            return sb.toString();
        }
        sb.append(System.lineSeparator()).append("You see these options:");
        List<DialogueResponse> responses = node.responses();
        for (int i = 0; i < responses.size(); i++) {
            sb.append(System.lineSeparator())
                .append("  ").append(i + 1).append(") ").append(responses.get(i).text());
        }
        return sb.toString();
    }

    /**
     * Resolves a 1-based response selection at the given node into the next node of the tree.
     *
     * @param tree          the active dialogue tree
     * @param currentNodeId the id of the node the player is currently at
     * @param responseNumber the 1-based response number the player selected
     * @return the target node, or empty when the current node is unknown, the number is out of range,
     *     or the target node is undefined
     */
    public Optional<DialogueNode> respond(DialogueTree tree, String currentNodeId, int responseNumber) {
        Objects.requireNonNull(tree, "Dialogue tree is required");
        Objects.requireNonNull(currentNodeId, "Current node id is required");
        DialogueNode current = tree.node(currentNodeId).orElse(null);
        if (current == null) {
            return Optional.empty();
        }
        List<DialogueResponse> responses = current.responses();
        if (responseNumber < 1 || responseNumber > responses.size()) {
            return Optional.empty();
        }
        DialogueResponse chosen = responses.get(responseNumber - 1);
        return tree.node(chosen.target());
    }
}
