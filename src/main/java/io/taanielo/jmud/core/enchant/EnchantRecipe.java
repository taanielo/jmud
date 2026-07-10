package io.taanielo.jmud.core.enchant;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.craft.RecipeMaterial;
import io.taanielo.jmud.core.world.AffixId;

/**
 * An enchanting recipe: the materials and gold a player must supply to an Enchanter to permanently
 * imbue a carried, equippable item with the stat affix identified by {@link #affixId()}.
 *
 * <p>Unlike a crafting {@link io.taanielo.jmud.core.craft.Recipe}, an enchanting recipe has no
 * output item — it names an affix (defined in {@code data/item-affixes.json}) that is appended to
 * the specific item instance being enchanted.
 *
 * @param id        the unique recipe id (e.g. {@code "enchant-of-the-bear"})
 * @param affixId   the id of the affix the recipe attaches; resolved via the affix repository
 * @param goldCost  the gold consumed by the enchantment; never negative
 * @param materials the non-empty list of material requirements consumed by the enchantment
 */
public record EnchantRecipe(String id, AffixId affixId, int goldCost, List<RecipeMaterial> materials) {

    public EnchantRecipe {
        Objects.requireNonNull(id, "Recipe id is required");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Recipe id must not be blank");
        }
        Objects.requireNonNull(affixId, "Affix id is required");
        if (goldCost < 0) {
            throw new IllegalArgumentException("Gold cost must not be negative");
        }
        materials = List.copyOf(Objects.requireNonNull(materials, "Materials are required"));
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("An enchant recipe must require at least one material");
        }
    }
}
