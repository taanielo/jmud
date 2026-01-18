package io.taanielo.jmud.core.healing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class HealingBaseResolverTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("jmud.healing.base_hp_per_tick");
        System.clearProperty("jmud.healing.race.elf.modifier");
        System.clearProperty("jmud.healing.class.mage.modifier");
        System.clearProperty("jmud.healing.race.troll.modifier");
        System.clearProperty("jmud.healing.class.warrior.modifier");
    }

    @Test
    void combinesRaceAndClassModifiers() {
        System.setProperty("jmud.healing.base_hp_per_tick", "5");
        System.setProperty("jmud.healing.race.elf.modifier", "-2");
        System.setProperty("jmud.healing.class.mage.modifier", "-1");
        System.setProperty("jmud.healing.race.troll.modifier", "2");
        System.setProperty("jmud.healing.class.warrior.modifier", "3");
        HealingBaseResolver resolver = new HealingBaseResolver();

        Player elfMage = playerWith(RaceId.of("elf"), ClassId.of("mage"));
        Player trollWarrior = playerWith(RaceId.of("troll"), ClassId.of("warrior"));

        assertEquals(2, resolver.baseHpPerTick(elfMage));
        assertEquals(10, resolver.baseHpPerTick(trollWarrior));
    }

    private Player playerWith(RaceId race, ClassId classId) {
        return new Player(
            User.of(Username.of("sparky"), Password.of("pw")),
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
}
