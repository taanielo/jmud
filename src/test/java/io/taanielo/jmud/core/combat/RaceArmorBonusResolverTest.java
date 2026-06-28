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

class RaceArmorBonusResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsArmorBonusForRace() throws Exception {
        seedRace(tempDir, "dwarf", 2);
        RaceArmorBonusResolver resolver = new RaceArmorBonusResolver(new JsonRaceRepository(tempDir));
        Player dwarf = playerWithRace(RaceId.of("dwarf"));

        assertEquals(2, resolver.armorBonus(dwarf));
    }

    @Test
    void returnsZeroForRaceWithNoArmorBonus() throws Exception {
        seedRace(tempDir, "human", 0);
        RaceArmorBonusResolver resolver = new RaceArmorBonusResolver(new JsonRaceRepository(tempDir));
        Player human = playerWithRace(RaceId.of("human"));

        assertEquals(0, resolver.armorBonus(human));
    }

    @Test
    void returnsZeroWhenPlayerHasNoRace() throws Exception {
        RaceArmorBonusResolver resolver = new RaceArmorBonusResolver(new JsonRaceRepository(tempDir));
        Player noRace = playerWithRace(null);

        assertEquals(0, resolver.armorBonus(noRace));
    }

    @Test
    void returnsZeroWhenRaceNotFound() throws Exception {
        RaceArmorBonusResolver resolver = new RaceArmorBonusResolver(new JsonRaceRepository(tempDir));
        Player unknown = playerWithRace(RaceId.of("unknown"));

        assertEquals(0, resolver.armorBonus(unknown));
    }

    @Test
    void noOpResolverAlwaysReturnsZero() {
        RaceArmorBonusResolver resolver = RaceArmorBonusResolver.noOp();
        Player player = playerWithRace(null);

        assertEquals(0, resolver.armorBonus(player));
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

    private void seedRace(Path dataRoot, String id, int armorBonus) throws Exception {
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
              "armor_bonus": %d
            }
            """, id, id.substring(0, 1).toUpperCase() + id.substring(1), armorBonus));
    }
}
