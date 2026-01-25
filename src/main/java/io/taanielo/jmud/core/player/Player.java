package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.combat.Combatant;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectTarget;

@Getter
public class Player implements EffectTarget, Combatant {
    private final User user;
    private final int level;
    private final long experience;
    private final PlayerVitals vitals;
    private final List<EffectInstance> effects;
    private final String promptFormat;
    private final boolean ansiEnabled;
    private final List<AbilityId> learnedAbilities;
    @JsonProperty("race")
    private final RaceId race;
    @JsonProperty("class")
    private final ClassId classId;
    private final boolean dead;

    public static Player of(User user, String promptFormat) {
        return new Player(user, 1, 0, PlayerVitals.defaults(), List.of(), promptFormat, false, List.of(), null, null);
    }

    public static Player of(User user, String promptFormat, boolean ansiEnabled) {
        return new Player(user, 1, 0, PlayerVitals.defaults(), List.of(), promptFormat, ansiEnabled, List.of(), null, null);
    }

    public static Player of(User user, String promptFormat, boolean ansiEnabled, List<AbilityId> learnedAbilities) {
        return new Player(user, 1, 0, PlayerVitals.defaults(), List.of(), promptFormat, ansiEnabled, learnedAbilities, null, null);
    }

    public Player(
        User user,
        int level,
        long experience,
        PlayerVitals vitals,
        List<EffectInstance> effects,
        String promptFormat,
        Boolean ansiEnabled,
        List<AbilityId> learnedAbilities,
        RaceId race,
        ClassId classId
    ) {
        this(user, level, experience, vitals, effects, promptFormat, ansiEnabled, learnedAbilities, race, classId, false);
    }

    @JsonCreator
    public Player(
        @JsonProperty("user") User user,
        @JsonProperty("level") int level,
        @JsonProperty("experience") long experience,
        @JsonProperty("vitals") PlayerVitals vitals,
        @JsonProperty("effects") List<EffectInstance> effects,
        @JsonProperty("promptFormat") String promptFormat,
        @JsonProperty("ansiEnabled") Boolean ansiEnabled,
        @JsonProperty("learnedAbilities") List<AbilityId> learnedAbilities,
        @JsonProperty("race") RaceId race,
        @JsonProperty("class") ClassId classId,
        @JsonProperty("dead") Boolean dead
    ) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.level = level;
        this.experience = experience;
        this.vitals = Objects.requireNonNull(vitals, "Vitals are required");
        this.effects = new ArrayList<>(Objects.requireNonNullElse(effects, List.of()));
        this.promptFormat = Objects.requireNonNull(promptFormat, "Prompt format is required");
        this.ansiEnabled = Objects.requireNonNullElse(ansiEnabled, false);
        this.learnedAbilities = List.copyOf(Objects.requireNonNullElse(learnedAbilities, List.of()));
        this.race = Objects.requireNonNullElse(race, RaceId.of("human"));
        this.classId = Objects.requireNonNullElse(classId, ClassId.of("adventurer"));
        boolean resolvedDead = Objects.requireNonNullElse(dead, false) || vitals.hp() <= 0;
        this.dead = resolvedDead;
    }

    @JsonIgnore
    public Username getUsername() {
        return user.getUsername();
    }

    @JsonIgnore
    public Password getPassword() {
        return user.getPassword();
    }

    public List<EffectInstance> effects() {
        return effects;
    }

    public Player die() {
        if (dead && vitals.hp() <= 0 && effects.isEmpty()) {
            return this;
        }
        PlayerVitals deadVitals = vitals.damage(vitals.hp());
        return new Player(user, level, experience, deadVitals, List.of(), promptFormat, ansiEnabled, learnedAbilities, race, classId, true);
    }

    public Player respawn() {
        PlayerVitals restored = vitals.respawnHalf();
        return new Player(user, level, experience, restored, List.of(), promptFormat, ansiEnabled, learnedAbilities, race, classId, false);
    }

    public Player withoutEffects() {
        return new Player(user, level, experience, vitals, List.of(), promptFormat, ansiEnabled, learnedAbilities, race, classId, dead);
    }

    public Player withAnsiEnabled(boolean enabled) {
        return new Player(user, level, experience, vitals, effects, promptFormat, enabled, learnedAbilities, race, classId, dead);
    }

    public Player withVitals(PlayerVitals updatedVitals) {
        return new Player(user, level, experience, updatedVitals, effects, promptFormat, ansiEnabled, learnedAbilities, race, classId, dead);
    }

    public Player withDead(boolean dead) {
        return new Player(user, level, experience, vitals, effects, promptFormat, ansiEnabled, learnedAbilities, race, classId, dead);
    }

    public Player withLearnedAbilities(List<AbilityId> abilities) {
        return new Player(user, level, experience, vitals, effects, promptFormat, ansiEnabled, abilities, race, classId, dead);
    }
}
