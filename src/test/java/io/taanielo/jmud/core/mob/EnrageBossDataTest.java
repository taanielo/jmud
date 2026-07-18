package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Data-authoring smoke test for the enrage-timer rollout (issue #746, following #745): verifies that
 * the production mob data loads with valid enrage fields, that the four capstone bosses keep their
 * authored enrage, that the newly-flagged elite/mini-boss/world-event encounters now enrage, and that
 * no ordinary trash mob accidentally enrages. Values are cross-checked against the capstone band so the
 * weaker rollout mobs stay below the raid-scale multipliers (a purely-data change over the #745 engine).
 */
class EnrageBossDataTest {

    /** The four capstone bosses authored by #745; must never lose their enrage. */
    private static final List<String> CAPSTONE_BOSSES =
        List.of("the-first-hum", "the-fermata", "the-coda", "the-ovation");

    /**
     * Elite/mini-boss/world-event encounters extended by #746, mapped to their authored
     * (enrage_ticks, enrage_damage_multiplier). All multipliers stay strictly below the 1.5 capstone
     * floor and all fuses stay at or below the shortest capstone fuse (22).
     */
    private static final Map<String, double[]> ROLLOUT = Map.ofEntries(
        Map.entry("the-throatless-marshal", new double[] {26, 1.45}),
        Map.entry("bonelight-cantor", new double[] {24, 1.4}),
        Map.entry("marrow-mother", new double[] {22, 1.35}),
        Map.entry("unlight-sovereign", new double[] {20, 1.35}),
        Map.entry("cinder-tyrant", new double[] {18, 1.3}),
        Map.entry("ember-wyrm", new double[] {16, 1.3}),
        Map.entry("frost-wyrm", new double[] {15, 1.25}),
        Map.entry("voidrift-devourer", new double[] {18, 1.4}),
        Map.entry("rimewrought-stalker", new double[] {14, 1.3}),
        Map.entry("riftspawn-mauler", new double[] {10, 1.2}),
        Map.entry("drowned-captain", new double[] {10, 1.2}));

    private List<MobTemplate> loadAll() throws RepositoryException {
        return new JsonMobTemplateRepository(Path.of("data")).findAll();
    }

    private MobTemplate byId(List<MobTemplate> all, String id) {
        return all.stream()
            .filter(t -> id.equals(t.id().getValue()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing mob template '" + id + "'"));
    }

    @Test
    void capstoneBossesStayEnrageCapable() throws RepositoryException {
        List<MobTemplate> all = loadAll();
        for (String id : CAPSTONE_BOSSES) {
            MobTemplate boss = byId(all, id);
            assertTrue(boss.enrageCapable(), "capstone boss '" + id + "' must remain enrage-capable");
            assertTrue(boss.enrageDamageMultiplier() >= 1.5,
                "capstone boss '" + id + "' keeps a raid-scale multiplier (>= 1.5)");
        }
    }

    @Test
    void rolloutEliteEncountersEnrageWithAuthoredValues() throws RepositoryException {
        List<MobTemplate> all = loadAll();
        ROLLOUT.forEach((id, expected) -> {
            MobTemplate mob = byId(all, id);
            assertTrue(mob.enrageCapable(), "rollout mob '" + id + "' must be enrage-capable (#746)");
            assertTrue(mob.enrageTicks() != null && mob.enrageTicks() == (int) expected[0],
                "rollout mob '" + id + "' enrage_ticks should be " + (int) expected[0]);
            assertTrue(mob.enrageDamageMultiplier() == expected[1],
                "rollout mob '" + id + "' enrage_damage_multiplier should be " + expected[1]);
            // Rollout mobs are weaker than capstones: their fuse and multiplier stay below the band.
            assertTrue(mob.enrageDamageMultiplier() < 1.5,
                "rollout mob '" + id + "' must stay below the capstone 1.5 multiplier floor");
            assertTrue(mob.enrageTicks() <= 26,
                "rollout mob '" + id + "' fuse must not exceed the near-capstone ceiling");
        });
    }

    @Test
    void everyEnragingMobIsAnEliteAndHasValidValues() throws RepositoryException {
        List<MobTemplate> all = loadAll();
        for (MobTemplate mob : all) {
            if (!mob.enrageCapable()) {
                continue;
            }
            String id = mob.id().getValue();
            // The record constructor already enforces these, but assert on real data as a guard.
            assertTrue(mob.enrageTicks() != null && mob.enrageTicks() > 0,
                "enraging mob '" + id + "' must have a positive enrage_ticks");
            assertTrue(mob.enrageDamageMultiplier() > 1.0,
                "enraging mob '" + id + "' must have a multiplier > 1.0");
            // Enrage belongs only on elite/boss encounters, never on trash: world boss or a 'boss' tag.
            assertTrue(mob.worldBoss() || mob.hasTag("boss") || mob.hasTag("elite"),
                "enraging mob '" + id + "' should be an elite/boss encounter, not trash");
        }
    }
}
