package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for owner-level scaling of {@link MobInstance} companions: a higher-level owner's
 * freshly tamed or summoned companion spawns with strictly more max HP and deals more damage than a
 * lower-level owner's companion of the identical template, while ordinary world mobs are unscaled.
 */
class MobInstanceCompanionScalingTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");
    private static final RoomId HOME_ROOM = RoomId.of("den");
    private static final Username OWNER = Username.of("ranger");
    private static final int TEMPLATE_MAX_HP = 40;

    private MobTemplate petTemplate() {
        return new MobTemplate(
            MobId.of("dire-wolf"),
            "Dire Wolf",
            TEMPLATE_MAX_HP,
            null,
            null,
            false,
            List.of(),
            SPAWN_ROOM,
            1,
            0,
            0,
            null,
            List.of("beast", "pet"),
            false,
            null,
            null,
            true);
    }

    @Test
    void ordinaryWorldMobIsUnscaled() {
        MobInstance mob = new MobInstance(petTemplate());

        assertEquals(TEMPLATE_MAX_HP, mob.maxHp(), "A world mob keeps its raw template max HP");
        assertEquals(7, mob.scaleCompanionDamage(7), "A world mob's damage is never scaled");
    }

    @Test
    void higherLevelTamerGetsTougherHarderHittingCompanion() {
        MobInstance low = MobInstance.tamed(petTemplate(), HOME_ROOM, OWNER, 6);
        MobInstance high = MobInstance.tamed(petTemplate(), HOME_ROOM, OWNER, 45);

        assertTrue(high.maxHp() > low.maxHp(),
            "A higher-level tamer's companion has strictly more max HP");
        assertEquals(high.maxHp(), high.currentHp(), "The tamed pet spawns at its scaled full HP");
        assertTrue(high.scaleCompanionDamage(10) > low.scaleCompanionDamage(10),
            "A higher-level tamer's companion deals strictly more damage");
    }

    @Test
    void higherLevelSummonerGetsTougherHarderHittingPet() {
        MobInstance low = MobInstance.summoned(petTemplate(), HOME_ROOM, OWNER, 6, 20);
        MobInstance high = MobInstance.summoned(petTemplate(), HOME_ROOM, OWNER, 45, 20);

        assertTrue(high.maxHp() > low.maxHp(),
            "A higher-level summoner's pet has strictly more max HP");
        assertTrue(high.scaleCompanionDamage(10) > low.scaleCompanionDamage(10),
            "A higher-level summoner's pet deals strictly more damage");
    }

    @Test
    void tamedScalingMatchesSummonedScalingForSameOwnerLevel() {
        MobInstance tamed = MobInstance.tamed(petTemplate(), HOME_ROOM, OWNER, 30);
        MobInstance summoned = MobInstance.summoned(petTemplate(), HOME_ROOM, OWNER, 30, 20);

        assertEquals(tamed.maxHp(), summoned.maxHp(),
            "TAME and SUMMON share one scaling formula, so equal owner levels give equal HP");
        assertEquals(tamed.scaleCompanionDamage(12), summoned.scaleCompanionDamage(12),
            "TAME and SUMMON share one scaling formula, so equal owner levels give equal damage");
    }

    @Test
    void respawnRestoresScaledMaxHpNotTemplateMax() {
        MobInstance pet = MobInstance.tamed(petTemplate(), HOME_ROOM, OWNER, 45);
        int scaledMax = pet.maxHp();
        pet.takeDamage(scaledMax);

        pet.respawn();

        assertEquals(scaledMax, pet.currentHp(), "Respawn restores the scaled max HP, not template max");
    }
}
