package io.taanielo.jmud.core.dialogue;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value object wrapping a dialogue-tree identifier string (e.g. {@code borin-blacksmith-welcome}).
 *
 * <p>A mob template links to a dialogue tree by this id via its {@code dialogue_id} field.
 */
public record DialogueId(String value) {

    public DialogueId {
        Objects.requireNonNull(value, "Dialogue id value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Dialogue id must not be blank");
        }
    }

    @JsonCreator
    public static DialogueId of(String value) {
        return new DialogueId(value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
