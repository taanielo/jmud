package io.taanielo.jmud.core.achievement;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value object wrapping an achievement identifier string (e.g. {@code first_kill}).
 *
 * <p>An {@link Achievement} definition is keyed by this id, and a player's
 * {@link PlayerAchievements} records unlock timestamps against it.
 */
public record AchievementId(String value) {

    public AchievementId {
        Objects.requireNonNull(value, "Achievement id value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Achievement id must not be blank");
        }
    }

    @JsonCreator
    public static AchievementId of(String value) {
        return new AchievementId(value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
