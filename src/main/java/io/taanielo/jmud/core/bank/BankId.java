package io.taanielo.jmud.core.bank;

import java.util.Objects;

/**
 * Value object identifying a bank NPC by its string id.
 */
public record BankId(String value) {

    public BankId {
        Objects.requireNonNull(value, "BankId value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("BankId value must not be blank");
        }
    }

    /** Creates a {@link BankId} from a raw string. */
    public static BankId of(String value) {
        return new BankId(value);
    }
}
