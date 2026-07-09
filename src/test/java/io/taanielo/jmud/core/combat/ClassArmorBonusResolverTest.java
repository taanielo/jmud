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
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class ClassArmorBonusResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsArmorBonusForClass() throws Exception {
        seedClass(tempDir, "paladin", 5);
        ClassArmorBonusResolver resolver = new ClassArmorBonusResolver(new JsonClassRepository(tempDir));
        Player paladin = playerWithClass(ClassId.of("paladin"));

        assertEquals(5, resolver.armorBonus(paladin));
    }

    @Test
    void returnsZeroForClassWithNoArmorBonus() throws Exception {
        seedClass(tempDir, "mage", 0);
        ClassArmorBonusResolver resolver = new ClassArmorBonusResolver(new JsonClassRepository(tempDir));
        Player mage = playerWithClass(ClassId.of("mage"));

        assertEquals(0, resolver.armorBonus(mage));
    }

    @Test
    void returnsZeroWhenPlayerHasNoClass() throws Exception {
        ClassArmorBonusResolver resolver = new ClassArmorBonusResolver(new JsonClassRepository(tempDir));
        Player noClass = playerWithClass(null);

        assertEquals(0, resolver.armorBonus(noClass));
    }

    @Test
    void returnsZeroWhenClassNotFound() throws Exception {
        ClassArmorBonusResolver resolver = new ClassArmorBonusResolver(new JsonClassRepository(tempDir));
        Player unknown = playerWithClass(ClassId.of("unknown"));

        assertEquals(0, resolver.armorBonus(unknown));
    }

    @Test
    void noOpResolverAlwaysReturnsZero() {
        ClassArmorBonusResolver resolver = ClassArmorBonusResolver.noOp();
        Player player = playerWithClass(ClassId.of("paladin"));

        assertEquals(0, resolver.armorBonus(player));
    }

    private Player playerWithClass(ClassId classId) {
        return new Player(
            User.of(Username.of("hero"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(),
            "prompt",
            false,
            List.of(AbilityId.of("spell.heal")),
            null,
            classId
        );
    }

    private void seedClass(Path dataRoot, String id, int armorBonus) throws Exception {
        Path classesDir = dataRoot.resolve("classes");
        java.nio.file.Files.createDirectories(classesDir);
        Path classFile = classesDir.resolve("class." + id + ".json");
        java.nio.file.Files.writeString(classFile, String.format("""
            {
              "schema_version": 3,
              "id": "%s",
              "name": "%s",
              "healing": {
                "base_modifier": 0
              },
              "carry_bonus": 10,
              "armor_bonus": %d
            }
            """, id, id.substring(0, 1).toUpperCase() + id.substring(1), armorBonus));
    }
}
