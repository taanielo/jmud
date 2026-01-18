package io.taanielo.jmud.core.authentication;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserRegistryImpl implements UserRegistry {

    private final Map<Username, User> users = new ConcurrentHashMap<>();

    public UserRegistryImpl() {
        User sparky = User.of(Username.of("sparky"), Password.of("qwerty"));
        users.put(sparky.getUsername(), sparky);
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public void register(User user) {
        users.putIfAbsent(user.getUsername(), user);
    }
}
