package io.taanielo.jmud.core.world;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Value;

/**
 * Identifier of a stat affix that can be attached to an {@link Item} (e.g. {@code "of-the-bear"}).
 * Affix definitions live in {@code data/item-affixes.json} and are resolved into bonus stats by
 * {@link ItemAffixService}.
 */
@Value
public class AffixId {
    String value;

    public AffixId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Affix id must not be blank");
        }
        this.value = value;
    }

    @JsonCreator
    public static AffixId of(String value) {
        return new AffixId(value);
    }

    @JsonValue
    public String jsonValue() {
        return value;
    }
}
