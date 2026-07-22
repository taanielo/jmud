package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.TimeOfDay;

/**
 * Unit tests for {@link MobTemplate}'s day/night respawn rate selection.
 */
class MobTemplateTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");

    private MobTemplate template(int respawnTicks, Integer nightRespawnTicks) {
        return new MobTemplate(
            MobId.of("mob.rat"),
            "Giant Rat",
            20,
            null,
            null,
            false,
            List.of(),
            SPAWN_ROOM,
            1,
            respawnTicks,
            5,
            null,
            List.of(),
            false,
            nightRespawnTicks
        );
    }

    @Test
    void nightRespawnTicksDefaultsToNullViaLegacyConstructor() {
        MobTemplate mobTemplate = new MobTemplate(
            MobId.of("mob.rat"), "Giant Rat", 20, null, null, false,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false);

        assertNull(mobTemplate.nightRespawnTicks());
        assertEquals(30, mobTemplate.respawnTicks(TimeOfDay.DAY));
        assertEquals(30, mobTemplate.respawnTicks(TimeOfDay.NIGHT));
    }

    @Test
    void usesDayRespawnTicksDuringDayEvenWhenNightRespawnTicksConfigured() {
        MobTemplate mobTemplate = template(30, 10);

        assertEquals(30, mobTemplate.respawnTicks(TimeOfDay.DAY));
    }

    @Test
    void usesNightRespawnTicksAtNightWhenConfigured() {
        MobTemplate mobTemplate = template(30, 10);

        assertEquals(10, mobTemplate.respawnTicks(TimeOfDay.NIGHT));
    }

    @Test
    void fallsBackToDayRespawnTicksAtNightWhenNightRespawnTicksNotConfigured() {
        MobTemplate mobTemplate = template(30, null);

        assertEquals(30, mobTemplate.respawnTicks(TimeOfDay.NIGHT));
    }

    @Test
    void rejectsNegativeNightRespawnTicks() {
        assertThrows(IllegalArgumentException.class, () -> template(30, -1));
    }

    @Test
    void parryChanceDefaultsToZeroViaLegacyConstructorSoExistingMobsNeverParry() {
        MobTemplate mobTemplate = template(30, null);

        assertEquals(0, mobTemplate.parryChancePercent(),
            "a template built via the pre-parry constructor defaults to no parry");
        assertFalse(mobTemplate.canParry(), "a zero parry chance means the mob cannot parry");
    }

    @Test
    void canParryReflectsAnAuthoredParryChance() {
        MobTemplate guard = new MobTemplate(
            MobId.of("mob.guard"), "Town Guard", 20, null, null, false,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false, null, null, false,
            null, null, false, false, 20);

        assertEquals(20, guard.parryChancePercent());
        assertTrue(guard.canParry(), "a positive parry chance means the mob can parry");
    }

    @Test
    void rejectsParryChanceAbove100() {
        assertThrows(IllegalArgumentException.class, () -> new MobTemplate(
            MobId.of("mob.guard"), "Town Guard", 20, null, null, false,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false, null, null, false,
            null, null, false, false, 101));
    }

    private MobTemplate elementalTemplate(
        Map<DamageType, Integer> resistances, Map<DamageType, Integer> vulnerabilities) {
        return new MobTemplate(
            MobId.of("mob.ice"), "Frost Wyrm", 200, null, null, false,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false, null, null, false,
            null, null, false, false, 0, resistances, vulnerabilities, null);
    }

    @Test
    void resistancesAndVulnerabilitiesDefaultToEmptyViaLegacyConstructor() {
        MobTemplate mobTemplate = template(30, null);

        assertEquals(0, mobTemplate.resistancePercent(DamageType.FIRE),
            "a template built via the pre-elemental constructor resists nothing");
        assertEquals(0, mobTemplate.vulnerabilityPercent(DamageType.COLD),
            "a template built via the pre-elemental constructor is weak to nothing");
    }

    @Test
    void elementalPercentsAreReportedForAuthoredTypes() {
        MobTemplate iceMob = elementalTemplate(
            Map.of(DamageType.COLD, 50), Map.of(DamageType.FIRE, 50));

        assertEquals(50, iceMob.resistancePercent(DamageType.COLD));
        assertEquals(0, iceMob.resistancePercent(DamageType.FIRE));
        assertEquals(50, iceMob.vulnerabilityPercent(DamageType.FIRE));
        assertEquals(0, iceMob.vulnerabilityPercent(DamageType.POISON));
        assertEquals(0, iceMob.resistancePercent(DamageType.PHYSICAL),
            "physical damage is never resisted");
    }

    @Test
    void rejectsResistancePercentAbove100() {
        assertThrows(IllegalArgumentException.class,
            () -> elementalTemplate(Map.of(DamageType.FIRE, 101), Map.of()));
    }

    @Test
    void rejectsVulnerabilityPercentAboveCap() {
        assertThrows(IllegalArgumentException.class,
            () -> elementalTemplate(Map.of(), Map.of(DamageType.FIRE, MobTemplate.MAX_VULNERABILITY_PERCENT + 1)));
    }

    @Test
    void rejectsPhysicalDamageTypeAsElementalKey() {
        assertThrows(IllegalArgumentException.class,
            () -> elementalTemplate(Map.of(DamageType.PHYSICAL, 20), Map.of()));
    }

    private MobTemplate reinforcementTemplate(
        Integer hpPercent, MobId addId, int count) {
        return new MobTemplate(
            MobId.of("mob.boss"), "the Boss", 100, null, null, true,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false,
            null, null, false, null, null, false, false, 0, Map.of(), Map.of(), null,
            null, 1.0, hpPercent, addId, count);
    }

    @Test
    void reinforcementDefaultsToNoneViaLegacyConstructor() {
        MobTemplate mobTemplate = template(30, null);

        assertFalse(mobTemplate.reinforcementCapable(),
            "a template built via a pre-reinforcement constructor never calls reinforcements");
        assertNull(mobTemplate.reinforcementHpPercent());
        assertNull(mobTemplate.reinforcementMobId());
        assertEquals(0, mobTemplate.reinforcementCount());
    }

    @Test
    void reinforcementCapableReflectsAuthoredFields() {
        MobTemplate boss = reinforcementTemplate(50, MobId.of("mob.add"), 3);

        assertTrue(boss.reinforcementCapable());
        assertEquals(50, boss.reinforcementHpPercent());
        assertEquals(MobId.of("mob.add"), boss.reinforcementMobId());
        assertEquals(3, boss.reinforcementCount());
    }

    @Test
    void rejectsReinforcementHpPercentOutOfRange() {
        assertThrows(IllegalArgumentException.class,
            () -> reinforcementTemplate(0, MobId.of("mob.add"), 3));
        assertThrows(IllegalArgumentException.class,
            () -> reinforcementTemplate(101, MobId.of("mob.add"), 3));
    }

    @Test
    void rejectsReinforcementThresholdWithoutAddId() {
        assertThrows(IllegalArgumentException.class,
            () -> reinforcementTemplate(50, null, 3));
    }

    @Test
    void rejectsNonPositiveReinforcementCountWhenThresholdPresent() {
        assertThrows(IllegalArgumentException.class,
            () -> reinforcementTemplate(50, MobId.of("mob.add"), 0));
    }
}
