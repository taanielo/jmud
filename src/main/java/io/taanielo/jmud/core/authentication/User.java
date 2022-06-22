package io.taanielo.jmud.core.authentication;

import lombok.Value;

@Value(staticConstructor = "of")
public class User {
    Username username;
    Password password;
}
