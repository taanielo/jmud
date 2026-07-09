package io.taanielo.jmud.core.dialogue;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A single selectable response within a {@link DialogueNode}.
 *
 * <p>Selecting a response advances the conversation to the node identified by {@link #target()}.
 * When {@link #grantQuestId()} is set, selecting the response also activates that quest for the
 * player (used to hand out NPC-delivery errands during a conversation).
 *
 * @param text         the response text shown to the player
 * @param target       the id of the node this response advances to
 * @param grantQuestId the id of a quest to grant when this response is chosen, or {@code null} for none
 */
public record DialogueResponse(String text, String target, @Nullable String grantQuestId) {

    public DialogueResponse {
        Objects.requireNonNull(text, "Response text is required");
        Objects.requireNonNull(target, "Response target is required");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Response text must not be blank");
        }
        if (target.isBlank()) {
            throw new IllegalArgumentException("Response target must not be blank");
        }
        if (grantQuestId != null && grantQuestId.isBlank()) {
            throw new IllegalArgumentException("Response grantQuestId must not be blank when present");
        }
    }

    /**
     * Creates a plain response that only advances the conversation, granting no quest.
     *
     * @param text   the response text shown to the player
     * @param target the id of the node this response advances to
     */
    public DialogueResponse(String text, String target) {
        this(text, target, null);
    }
}
