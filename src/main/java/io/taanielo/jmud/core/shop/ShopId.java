package io.taanielo.jmud.core.shop;

import java.util.Objects;

/**
 * Value object identifying a shop by its string id.
 */
public record ShopId(String value) {

    public ShopId {
        Objects.requireNonNull(value, "ShopId value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ShopId value must not be blank");
        }
    }

    /** Creates a {@link ShopId} from a raw string. */
    public static ShopId of(String value) {
        return new ShopId(value);
    }
}
