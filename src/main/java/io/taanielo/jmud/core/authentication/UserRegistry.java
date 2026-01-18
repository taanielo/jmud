package io.taanielo.jmud.core.authentication;

import java.util.Optional;

public interface UserRegistry {
    Optional<User> findByUsername(Username username);
    void register(User user);
}
