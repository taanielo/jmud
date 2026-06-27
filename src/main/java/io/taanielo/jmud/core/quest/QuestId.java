package io.taanielo.jmud.core.quest;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value object wrapping a quest identifier string.
 */
public record QuestId(String value) {

    public QuestId {
        Objects.requireNonNull(value, "Quest id value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Quest id must not be blank");
        }
    }

    @JsonCreator
    public static QuestId of(String value) {
        return new QuestId(value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
