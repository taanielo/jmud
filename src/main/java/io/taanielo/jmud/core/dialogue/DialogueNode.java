package io.taanielo.jmud.core.dialogue;

import java.util.List;
import java.util.Objects;

/**
 * A single node in a {@link DialogueTree}: the NPC's spoken line plus the ordered list of numbered
 * responses the player may choose from.
 *
 * <p>A node with no responses is <em>terminal</em> — reaching it ends the conversation after its
 * text is shown.
 */
public record DialogueNode(String id, String text, List<DialogueResponse> responses) {

    public DialogueNode {
        Objects.requireNonNull(id, "Node id is required");
        Objects.requireNonNull(text, "Node text is required");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Node id must not be blank");
        }
        responses = responses == null ? List.of() : List.copyOf(responses);
    }

    /**
     * Returns whether this node ends the conversation (has no further responses).
     *
     * @return {@code true} when the node has no responses
     */
    public boolean isTerminal() {
        return responses.isEmpty();
    }
}
