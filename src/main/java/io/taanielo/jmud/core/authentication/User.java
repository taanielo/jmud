package io.taanielo.jmud.core.authentication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class User {
    Username username;
    Password password;

    @JsonCreator
    public User(
        @JsonProperty("username") Username username,
        @JsonProperty("password") Password password
    ) {
        this.username = username;
        this.password = password;
    }

    public static User of(Username username, Password password) {
        return new User(username, password);
    }
}
