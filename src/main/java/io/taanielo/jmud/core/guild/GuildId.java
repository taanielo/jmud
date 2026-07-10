package io.taanielo.jmud.core.guild;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object identifying a guild by an opaque, stable string id.
 *
 * <p>The id is independent of the guild's display name (which may be renamed or reused after a
 * disband), so it is used as the guild's JSON file name and as the key in every in-memory index.
 */
public record GuildId(String value) {

    public GuildId {
        Objects.requireNonNull(value, "GuildId value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("GuildId value must not be blank");
        }
    }

    /** Creates a {@link GuildId} from a raw string. */
    public static GuildId of(String value) {
        return new GuildId(value);
    }

    /** Generates a fresh, random {@link GuildId} for a newly founded guild. */
    public static GuildId newId() {
        return new GuildId(UUID.randomUUID().toString());
    }
}
