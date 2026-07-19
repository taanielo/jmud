package io.taanielo.jmud.core.world;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Value;

/**
 * Identifier of an item set (see {@code data/item-sets/}). Member items reference their set through
 * this id; the {@link io.taanielo.jmud.core.combat.SetBonusResolver} groups equipped pieces by it to
 * award set threshold bonuses.
 */
@Value
public class ItemSetId {
    String value;

    public ItemSetId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Item set id must not be blank");
        }
        this.value = value;
    }

    @JsonCreator
    public static ItemSetId fromJson(@JsonProperty("value") String value) {
        return new ItemSetId(value);
    }

    @JsonValue
    public String jsonValue() {
        return value;
    }

    public static ItemSetId of(String value) {
        return new ItemSetId(value);
    }
}
