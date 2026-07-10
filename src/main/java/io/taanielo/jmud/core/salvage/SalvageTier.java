package io.taanielo.jmud.core.salvage;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.Rarity;

/**
 * The salvage yield for a single item {@link Rarity} tier: the materials (and quantities) a player
 * receives when they salvage an item of that tier. Common tiers yield a small amount of a basic
 * material, while rarer tiers yield more or better materials.
 *
 * @param rarity    the rarity tier this yield applies to
 * @param materials the materials produced by salvaging an item of this tier; never empty
 */
public record SalvageTier(Rarity rarity, List<SalvageMaterial> materials) {

    public SalvageTier {
        Objects.requireNonNull(rarity, "Rarity is required");
        materials = List.copyOf(Objects.requireNonNull(materials, "Materials are required"));
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("Salvage tier '" + rarity.id() + "' must yield at least one material");
        }
    }
}
