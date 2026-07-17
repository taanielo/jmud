package io.taanielo.jmud.core.player;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;

public class PlayerIdentity {
    /** Maximum length of a player's custom LOOK description, in characters. */
    public static final int MAX_DESCRIPTION_LENGTH = 240;

    private final User user;
    private final int level;
    private final long experience;
    private final RaceId race;
    private final ClassId classId;
    /** Custom roleplay LOOK description; never null, empty string means "not set". */
    private final String description;
    /**
     * Username of the player's spouse (see the MARRY command), or {@code null} when unmarried. Stored
     * as the spouse's {@link io.taanielo.jmud.core.authentication.Username} display value; matching is
     * case-insensitive via {@code Username}. Blank strings normalise to {@code null}.
     */
    private final @Nullable String spouse;

    public PlayerIdentity(User user, int level, long experience, RaceId race, ClassId classId) {
        this(user, level, experience, race, classId, "");
    }

    public PlayerIdentity(User user, int level, long experience, RaceId race, ClassId classId, String description) {
        this(user, level, experience, race, classId, description, null);
    }

    public PlayerIdentity(
        User user,
        int level,
        long experience,
        RaceId race,
        ClassId classId,
        String description,
        @Nullable String spouse
    ) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.level = level;
        this.experience = experience;
        this.race = Objects.requireNonNullElse(race, RaceId.of("human"));
        this.classId = Objects.requireNonNullElse(classId, ClassId.of("adventurer"));
        this.description = Objects.requireNonNullElse(description, "").trim();
        this.spouse = spouse == null || spouse.isBlank() ? null : spouse.trim();
    }

    public User user() {
        return user;
    }

    public int level() {
        return level;
    }

    public long experience() {
        return experience;
    }

    public RaceId race() {
        return race;
    }

    public ClassId classId() {
        return classId;
    }

    /**
     * Returns the player's custom LOOK description, or an empty string when none is set.
     */
    public String description() {
        return description;
    }

    /**
     * Returns the username of this player's spouse (see the MARRY command), or {@code null} when the
     * player is unmarried.
     */
    public @Nullable String spouse() {
        return spouse;
    }

    public PlayerIdentity withLevel(int nextLevel) {
        return new PlayerIdentity(user, nextLevel, experience, race, classId, description, spouse);
    }

    public PlayerIdentity withExperience(long nextExperience) {
        return new PlayerIdentity(user, level, nextExperience, race, classId, description, spouse);
    }

    public PlayerIdentity withRace(RaceId nextRace) {
        return new PlayerIdentity(user, level, experience, nextRace, classId, description, spouse);
    }

    public PlayerIdentity withClassId(ClassId nextClassId) {
        return new PlayerIdentity(user, level, experience, race, nextClassId, description, spouse);
    }

    /**
     * Returns a copy of this identity with the given custom LOOK description, trimmed; pass an empty
     * or {@code null} string to clear it back to the default generated line.
     *
     * @param newDescription the new description text, or {@code null}/blank to clear
     */
    public PlayerIdentity withDescription(String newDescription) {
        return new PlayerIdentity(user, level, experience, race, classId, newDescription, spouse);
    }

    /**
     * Returns a copy of this identity bonded to the given spouse, or unmarried when {@code newSpouse}
     * is {@code null}/blank.
     *
     * @param newSpouse the spouse's username display value, or {@code null}/blank to clear the bond
     */
    public PlayerIdentity withSpouse(@Nullable String newSpouse) {
        return new PlayerIdentity(user, level, experience, race, classId, description, newSpouse);
    }
}
