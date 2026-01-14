package io.taanielo.jmud.core.authentication;

import lombok.Value;
import lombok.AllArgsConstructor;

@Value
@AllArgsConstructor
public class User {
    Username username;
    Password password;

    public static User of(Username username, Password password) {
        return new User(username, password);
    }
}
