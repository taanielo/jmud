package io.taanielo.jmud.core.authentication;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class UserRegistryImplTest {

    @Test
    void registersUsersConcurrently() throws InterruptedException {
        UserRegistryImpl registry = new UserRegistryImpl();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<User> users = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            users.add(User.of(Username.of("user" + i), Password.of("pw" + i)));
        }

        for (int i = 0; i < threads; i++) {
            User user = users.get(i);
            pool.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    registry.register(user);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(2, TimeUnit.SECONDS);
        start.countDown();
        done.await(2, TimeUnit.SECONDS);
        pool.shutdownNow();

        for (User user : users) {
            assertTrue(registry.findByUsername(user.getUsername()).isPresent());
        }
    }
}
