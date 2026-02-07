package io.taanielo.jmud.core.player;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;

public class PlayerIdentity {
    private final User user;
    private final int level;
    private final long experience;
    private final RaceId race;
    private final ClassId classId;

    public PlayerIdentity(User user, int level, long experience, RaceId race, ClassId classId) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.level = level;
        this.experience = experience;
        this.race = Objects.requireNonNullElse(race, RaceId.of("human"));
        this.classId = Objects.requireNonNullElse(classId, ClassId.of("adventurer"));
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

    public PlayerIdentity withLevel(int nextLevel) {
        return new PlayerIdentity(user, nextLevel, experience, race, classId);
    }

    public PlayerIdentity withExperience(long nextExperience) {
        return new PlayerIdentity(user, level, nextExperience, race, classId);
    }

    public PlayerIdentity withRace(RaceId nextRace) {
        return new PlayerIdentity(user, level, experience, nextRace, classId);
    }

    public PlayerIdentity withClassId(ClassId nextClassId) {
        return new PlayerIdentity(user, level, experience, race, nextClassId);
    }
}
