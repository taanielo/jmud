package io.taanielo.jmud.core.player;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.guild.GuildId;

/**
 * Immutable player component recording which guild, if any, the player belongs to.
 *
 * <p>This is a persisted mirror of the authoritative roster held by
 * {@link io.taanielo.jmud.core.guild.GuildService}: it lets a player's guild membership be saved with
 * their character and survive logout and server restarts. A {@code null} guild id (the default for
 * existing save files, where the field is simply absent) means the player is in no guild.
 */
public final class PlayerGuildMembership {

    private static final PlayerGuildMembership NONE = new PlayerGuildMembership((GuildId) null);

    private final @Nullable GuildId guildId;

    public PlayerGuildMembership(@Nullable GuildId guildId) {
        this.guildId = guildId;
    }

    /** Returns the shared "no guild" instance. */
    public static PlayerGuildMembership none() {
        return NONE;
    }

    /**
     * Creates a membership from a raw guild id string, treating {@code null}/blank as no guild.
     *
     * @param guildId the persisted guild id string, or {@code null}
     */
    public static PlayerGuildMembership fromId(@Nullable String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return NONE;
        }
        return new PlayerGuildMembership(GuildId.of(guildId));
    }

    /**
     * Creates a membership for the given guild id.
     *
     * @param guildId the guild the player belongs to; must not be null
     */
    public static PlayerGuildMembership of(GuildId guildId) {
        return new PlayerGuildMembership(guildId);
    }

    /** Returns the guild id, or {@code null} when the player is in no guild. */
    public @Nullable GuildId guildId() {
        return guildId;
    }

    /** Returns {@code true} when the player currently belongs to a guild. */
    public boolean hasGuild() {
        return guildId != null;
    }
}
