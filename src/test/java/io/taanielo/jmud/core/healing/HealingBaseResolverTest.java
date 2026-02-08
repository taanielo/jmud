package io.taanielo.jmud.core.healing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class HealingBaseResolverTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty("jmud.healing.base_hp_per_tick");
    }

    @Test
    void combinesRaceAndClassModifiers() throws Exception {
        System.setProperty("jmud.healing.base_hp_per_tick", "5");
        seedClass(tempDir, "mage", -1);
        seedClass(tempDir, "warrior", 3);
        seedRace(tempDir, "elf", -2);
        seedRace(tempDir, "troll", 2);
        HealingBaseResolver resolver = new HealingBaseResolver(
            new JsonRaceRepository(tempDir),
            new JsonClassRepository(tempDir)
        );

        Player elfMage = playerWith(RaceId.of("elf"), ClassId.of("mage"));
        Player trollWarrior = playerWith(RaceId.of("troll"), ClassId.of("warrior"));

        assertEquals(2, resolver.baseHpPerTick(elfMage));
        assertEquals(10, resolver.baseHpPerTick(trollWarrior));
    }

    private Player playerWith(RaceId race, ClassId classId) {
        return new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(),
            "prompt",
            false,
            List.of(AbilityId.of("spell.heal")),
            race,
            classId
        );
    }

    private void seedRace(Path dataRoot, String id, int modifier) throws Exception {
        Path racesDir = dataRoot.resolve("races");
        java.nio.file.Files.createDirectories(racesDir);
        Path raceFile = racesDir.resolve("race." + id + ".json");
        java.nio.file.Files.writeString(raceFile, String.format("""
            {
              "schema_version": 1,
              "id": "%s",
              "name": "%s",
              "healing": {
                "base_modifier": %d
              }
            }
            """, id, id.substring(0, 1).toUpperCase() + id.substring(1), modifier));
    }

    private void seedClass(Path dataRoot, String id, int modifier) throws Exception {
        Path classesDir = dataRoot.resolve("classes");
        java.nio.file.Files.createDirectories(classesDir);
        Path classFile = classesDir.resolve("class." + id + ".json");
        java.nio.file.Files.writeString(classFile, String.format("""
            {
              "schema_version": 1,
              "id": "%s",
              "name": "%s",
              "healing": {
                "base_modifier": %d
              }
            }
            """, id, id.substring(0, 1).toUpperCase() + id.substring(1), modifier));
    }
}
