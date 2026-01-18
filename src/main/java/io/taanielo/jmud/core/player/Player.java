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
    private final List<EffectInstance> effects;

    public static Player of(User user) {
        return new Player(user, 1, 0, List.of());
    }

    @JsonCreator
    public Player(
        @JsonProperty("user") User user,
        @JsonProperty("level") int level,
        @JsonProperty("experience") long experience,
        @JsonProperty("effects") List<EffectInstance> effects
    ) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.level = level;
        this.experience = experience;
        this.effects = new ArrayList<>(Objects.requireNonNullElse(effects, List.of()));
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
