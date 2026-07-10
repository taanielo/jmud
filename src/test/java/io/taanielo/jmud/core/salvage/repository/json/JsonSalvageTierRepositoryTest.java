package io.taanielo.jmud.core.salvage.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.salvage.SalvageTier;
import io.taanielo.jmud.core.salvage.SalvageTierRepositoryException;
import io.taanielo.jmud.core.world.Rarity;

/**
 * Verifies that {@link JsonSalvageTierRepository} loads the seeded rarity-to-material mapping from
 * {@code data/salvage/salvage-tiers.json} and rejects malformed or unsupported data.
 */
class JsonSalvageTierRepositoryTest {

    @Test
    void findAll_loadsSeededTiers() throws SalvageTierRepositoryException {
        JsonSalvageTierRepository repo = new JsonSalvageTierRepository(Path.of("data"));

        List<SalvageTier> tiers = repo.findAll();

        Set<Rarity> rarities = tiers.stream().map(SalvageTier::rarity).collect(Collectors.toSet());
        assertTrue(rarities.contains(Rarity.COMMON), "Expected a common tier");
        assertTrue(rarities.contains(Rarity.UNCOMMON), "Expected an uncommon tier");
        assertTrue(rarities.contains(Rarity.RARE), "Expected a rare tier");
        assertTrue(tiers.stream().allMatch(t -> !t.materials().isEmpty()),
            "Every tier must yield at least one material");
    }

    @Test
    void findAll_rejectsUnsupportedSchemaVersion(@TempDir Path tempDir) throws Exception {
        Path salvageDir = tempDir.resolve("salvage");
        Files.createDirectories(salvageDir);
        Files.writeString(salvageDir.resolve("salvage-tiers.json"), """
            {
              "schema_version": 99,
              "tiers": [
                { "rarity": "common", "materials": [ { "item": "iron-ore", "quantity": 1 } ] }
              ]
            }
            """);

        JsonSalvageTierRepository repo = new JsonSalvageTierRepository(tempDir);

        SalvageTierRepositoryException ex =
            assertThrows(SalvageTierRepositoryException.class, repo::findAll);
        assertTrue(ex.getMessage().contains("schema version"), ex.getMessage());
    }

    @Test
    void findAll_rejectsMissingFile(@TempDir Path tempDir) {
        JsonSalvageTierRepository repo = new JsonSalvageTierRepository(tempDir);

        SalvageTierRepositoryException ex =
            assertThrows(SalvageTierRepositoryException.class, repo::findAll);
        assertTrue(ex.getMessage().contains("not found"), ex.getMessage());
    }

    @Test
    void findAll_parsesMaterialQuantities(@TempDir Path tempDir) throws Exception {
        Path salvageDir = tempDir.resolve("salvage");
        Files.createDirectories(salvageDir);
        Files.writeString(salvageDir.resolve("salvage-tiers.json"), """
            {
              "schema_version": 1,
              "tiers": [
                {
                  "rarity": "rare",
                  "materials": [
                    { "item": "iron-ore", "quantity": 3 },
                    { "item": "arcane-dust" }
                  ]
                }
              ]
            }
            """);

        JsonSalvageTierRepository repo = new JsonSalvageTierRepository(tempDir);
        List<SalvageTier> tiers = repo.findAll();

        assertEquals(1, tiers.size());
        SalvageTier rare = tiers.get(0);
        assertEquals(Rarity.RARE, rare.rarity());
        assertEquals(3, rare.materials().get(0).quantity(), "explicit quantity parsed");
        assertEquals(1, rare.materials().get(1).quantity(), "omitted quantity defaults to 1");
    }
}
