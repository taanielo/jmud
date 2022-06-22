package io.taanielo.jmud.core.authentication;

import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserRegistryImpl implements UserRegistry {

    private static final Set<User> DUMMY_USERS = Set.of(
            User.of(Username.of("sparky"), Password.of("qwerty")),
            User.of(Username.of("bob"), Password.of("123456")),
            User.of(Username.of("MudGod"), Password.of("pasSWord"))
    );

    @Override
    public Optional<User> findByUsername(Username username) {
        return DUMMY_USERS.stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }
}
