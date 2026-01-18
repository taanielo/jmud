package io.taanielo.jmud.core.authentication;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserRegistryImpl implements UserRegistry {

    private final Set<User> users = new LinkedHashSet<>(
        Set.of(
            User.of(Username.of("sparky"), Password.of("qwerty"))
        )
    );

    @Override
    public Optional<User> findByUsername(Username username) {
        return users.stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public void register(User user) {
        users.add(user);
    }
}
