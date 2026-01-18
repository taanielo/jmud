package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectTarget;

@Getter
public class Player implements EffectTarget {
    private final User user;
    private final int level;
    private final long experience;
    private final PlayerVitals vitals;
    private final List<EffectInstance> effects;
    private final String promptFormat;

    public static Player of(User user, String promptFormat) {
        return new Player(user, 1, 0, PlayerVitals.defaults(), List.of(), promptFormat);
    }

    @JsonCreator
    public Player(
        @JsonProperty("user") User user,
        @JsonProperty("level") int level,
        @JsonProperty("experience") long experience,
        @JsonProperty("vitals") PlayerVitals vitals,
        @JsonProperty("effects") List<EffectInstance> effects,
        @JsonProperty("promptFormat") String promptFormat
    ) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.level = level;
        this.experience = experience;
        this.vitals = Objects.requireNonNull(vitals, "Vitals are required");
        this.effects = new ArrayList<>(Objects.requireNonNullElse(effects, List.of()));
        this.promptFormat = Objects.requireNonNull(promptFormat, "Prompt format is required");
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
}
