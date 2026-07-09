package io.taanielo.jmud.core.dialogue;

import java.util.Objects;

/**
 * A single selectable response within a {@link DialogueNode}.
 *
 * <p>Selecting a response advances the conversation to the node identified by {@link #target()}.
 */
public record DialogueResponse(String text, String target) {

    public DialogueResponse {
        Objects.requireNonNull(text, "Response text is required");
        Objects.requireNonNull(target, "Response target is required");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Response text must not be blank");
        }
        if (target.isBlank()) {
            throw new IllegalArgumentException("Response target must not be blank");
        }
    }
}
