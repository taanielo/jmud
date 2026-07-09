package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class RaceAttackBonusResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsAttackModifierForRace() throws Exception {
        seedRace(tempDir, "orc", 5);
        RaceAttackBonusResolver resolver = new RaceAttackBonusResolver(new JsonRaceRepository(tempDir));
        Player orc = playerWithRace(RaceId.of("orc"));

        assertEquals(5, resolver.attackBonus(orc));
    }

    @Test
    void returnsNegativeAttackModifier() throws Exception {
        seedRace(tempDir, "elf", -1);
        RaceAttackBonusResolver resolver = new RaceAttackBonusResolver(new JsonRaceRepository(tempDir));
        Player elf = playerWithRace(RaceId.of("elf"));

        assertEquals(-1, resolver.attackBonus(elf));
    }

    @Test
    void returnsZeroWhenPlayerHasNoRace() throws Exception {
        RaceAttackBonusResolver resolver = new RaceAttackBonusResolver(new JsonRaceRepository(tempDir));
        Player noRace = playerWithRace(null);

        assertEquals(0, resolver.attackBonus(noRace));
    }

    @Test
    void returnsZeroWhenRaceNotFound() throws Exception {
        RaceAttackBonusResolver resolver = new RaceAttackBonusResolver(new JsonRaceRepository(tempDir));
        Player unknown = playerWithRace(RaceId.of("unknown"));

        assertEquals(0, resolver.attackBonus(unknown));
    }

    @Test
    void noOpResolverAlwaysReturnsZero() {
        RaceAttackBonusResolver resolver = RaceAttackBonusResolver.noOp();
        Player player = playerWithRace(null);

        assertEquals(0, resolver.attackBonus(player));
    }

    private Player playerWithRace(RaceId raceId) {
        return new Player(
            User.of(Username.of("hero"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(),
            "prompt",
            false,
            List.of(AbilityId.of("spell.heal")),
            raceId,
            null
        );
    }

    private void seedRace(Path dataRoot, String id, int attackModifier) throws Exception {
        Path racesDir = dataRoot.resolve("races");
        java.nio.file.Files.createDirectories(racesDir);
        Path raceFile = racesDir.resolve("race." + id + ".json");
        java.nio.file.Files.writeString(raceFile, String.format("""
            {
              "schema_version": 2,
              "id": "%s",
              "name": "%s",
              "healing": {
                "base_modifier": 0
              },
              "carry_base": 50,
              "attack_modifier": %d
            }
            """, id, id.substring(0, 1).toUpperCase() + id.substring(1), attackModifier));
    }
}
