package io.taanielo.jmud.core.player;

import java.util.Objects;

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

    public PlayerIdentity(User user, int level, long experience, RaceId race, ClassId classId) {
        this(user, level, experience, race, classId, "");
    }

    public PlayerIdentity(User user, int level, long experience, RaceId race, ClassId classId, String description) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.level = level;
        this.experience = experience;
        this.race = Objects.requireNonNullElse(race, RaceId.of("human"));
        this.classId = Objects.requireNonNullElse(classId, ClassId.of("adventurer"));
        this.description = Objects.requireNonNullElse(description, "").trim();
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

    public PlayerIdentity withLevel(int nextLevel) {
        return new PlayerIdentity(user, nextLevel, experience, race, classId, description);
    }

    public PlayerIdentity withExperience(long nextExperience) {
        return new PlayerIdentity(user, level, nextExperience, race, classId, description);
    }

    public PlayerIdentity withRace(RaceId nextRace) {
        return new PlayerIdentity(user, level, experience, nextRace, classId, description);
    }

    public PlayerIdentity withClassId(ClassId nextClassId) {
        return new PlayerIdentity(user, level, experience, race, nextClassId, description);
    }

    /**
     * Returns a copy of this identity with the given custom LOOK description, trimmed; pass an empty
     * or {@code null} string to clear it back to the default generated line.
     *
     * @param newDescription the new description text, or {@code null}/blank to clear
     */
    public PlayerIdentity withDescription(String newDescription) {
        return new PlayerIdentity(user, level, experience, race, classId, newDescription);
    }
}
