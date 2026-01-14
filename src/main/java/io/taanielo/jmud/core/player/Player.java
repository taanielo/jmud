package io.taanielo.jmud.core.player;

import lombok.Value;

import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.authentication.Password;

@Value
public class Player {
    User user;
    int level;
    long experience;

    public static Player of(User user) {
        return new Player(user, 1, 0);
    }

    public Player(User user, int level, long experience) {
        this.user = user;
        this.level = level;
        this.experience = experience;
    }

    public Username getUsername() {
        return user.getUsername();
    }

    public Password getPassword() {
        return user.getPassword();
    }
}
