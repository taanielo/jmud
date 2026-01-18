package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;

class JsonPlayerRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsPlayerWithEffects() {
        JsonPlayerRepository repository = new JsonPlayerRepository(tempDir);
        User user = User.of(Username.of("sparky"), Password.of("qwerty"));
        List<EffectInstance> effects = List.of(new EffectInstance(EffectId.of("stoneskin"), 5, 1));
        PlayerVitals vitals = new PlayerVitals(12, 20, 6, 10, 8, 15);
        Player player = new Player(user, 3, 42, vitals, effects, "HP {hp}/{maxHp} Exp {exp}");

        repository.savePlayer(player);

        Optional<Player> loaded = repository.loadPlayer(user.getUsername());
        assertTrue(loaded.isPresent());
        assertEquals("sparky", loaded.get().getUsername().getValue());
        assertEquals(3, loaded.get().getLevel());
        assertEquals(42, loaded.get().getExperience());
        assertEquals(12, loaded.get().getVitals().hp());
        assertEquals(20, loaded.get().getVitals().maxHp());
        assertEquals("HP {hp}/{maxHp} Exp {exp}", loaded.get().getPromptFormat());
        assertEquals(1, loaded.get().effects().size());
        assertEquals("stoneskin", loaded.get().effects().get(0).id().getValue());
    }
}
