package io.taanielo.jmud.core.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Application service for crafting: listing known recipes with live material availability and turning
 * gathered materials plus gold into upgraded gear.
 *
 * <p>All operations are pure with respect to the {@link Player}: the player passed in is never
 * mutated. On success the caller receives an updated player copy inside the returned
 * {@link CraftOutcome}, which it applies on the tick thread (AGENTS.md §5). The service is stateless
 * apart from its immutable recipe table, so a single instance is safe to share.
 */
public class CraftingService {

    private final List<Recipe> recipes;
    private final ItemRepository itemRepository;

    /**
     * Creates a crafting service over a fixed set of recipes.
     *
     * @param recipes        the known recipes; copied defensively, may be empty
     * @param itemRepository repository used to resolve material and output item definitions
     */
    public CraftingService(List<Recipe> recipes, ItemRepository itemRepository) {
        this.recipes = List.copyOf(Objects.requireNonNull(recipes, "Recipes are required"));
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
    }

    /**
     * Returns the known recipes.
     *
     * @return an immutable list of recipes, possibly empty
     */
    public List<Recipe> recipes() {
        return recipes;
    }

    /**
     * Produces a formatted listing of every known recipe, showing each output item, its material
     * requirements with the player's current {@code have X / need Y} counts, and the gold cost.
     *
     * @param player the player whose inventory the have-counts are measured against
     * @return lines ready to be sent to the player's connection
     */
    public List<String> formatRecipes(Player player) {
        Objects.requireNonNull(player, "Player is required");
        List<String> lines = new ArrayList<>();
        if (recipes.isEmpty()) {
            lines.add("The blacksmith has no recipes to offer.");
            return lines;
        }
        lines.add("The blacksmith can craft the following (CRAFT <item> to make one):");
        for (Recipe recipe : recipes) {
            List<String> parts = new ArrayList<>();
            for (RecipeMaterial material : recipe.materials()) {
                int have = countInInventory(player, material.itemId());
                parts.add(String.format("%s (have %d / need %d)",
                    itemName(material.itemId()), have, material.quantity()));
            }
            lines.add(String.format("  %s — %s; %d gold",
                resolveOutputName(recipe), String.join(", ", parts), recipe.goldCost()));
        }
        return lines;
    }

    /**
     * Attempts to craft the recipe matching the given input on behalf of the player.
     *
     * <p>On success the required material quantities and gold are consumed and the crafted item is
     * added to the player's inventory. On failure the returned message names the missing material(s)
     * and/or gold shortfall and nothing is consumed.
     *
     * @param player the crafting player
     * @param input  the recipe or output item name (or prefix) to craft
     * @return the outcome describing success or the reason for failure
     */
    public CraftOutcome craft(Player player, @Nullable String input) {
        Objects.requireNonNull(player, "Player is required");
        if (input == null || input.isBlank()) {
            return CraftOutcome.failure("Craft what? Type CRAFT to see what the blacksmith can make.");
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        Optional<Recipe> recipeOpt = findRecipe(normalized);
        if (recipeOpt.isEmpty()) {
            return CraftOutcome.failure("The blacksmith knows no recipe for '" + input.trim() + "'.");
        }
        Recipe recipe = recipeOpt.get();

        List<String> shortfalls = new ArrayList<>();
        for (RecipeMaterial material : recipe.materials()) {
            int have = countInInventory(player, material.itemId());
            if (have < material.quantity()) {
                shortfalls.add((material.quantity() - have) + " more "
                    + itemName(material.itemId()));
            }
        }
        if (player.getGold() < recipe.goldCost()) {
            shortfalls.add((recipe.goldCost() - player.getGold()) + " more gold");
        }
        if (!shortfalls.isEmpty()) {
            return CraftOutcome.failure(
                "You cannot craft the " + resolveOutputName(recipe) + " yet. You still need: "
                    + String.join(", ", shortfalls) + ".");
        }

        Item output;
        try {
            Optional<Item> outputOpt = itemRepository.findById(recipe.outputItemId());
            if (outputOpt.isEmpty()) {
                return CraftOutcome.failure(
                    "The blacksmith fumbles — the " + resolveOutputName(recipe)
                        + " cannot be made right now.");
            }
            output = outputOpt.get();
        } catch (RepositoryException e) {
            return CraftOutcome.failure(
                "The blacksmith fumbles — the " + resolveOutputName(recipe)
                    + " cannot be made right now.");
        }

        List<Item> inventory = new ArrayList<>(player.getInventory());
        for (RecipeMaterial material : recipe.materials()) {
            consume(inventory, material.itemId(), material.quantity());
        }
        Player updated = player.withInventory(inventory).addGold(-recipe.goldCost()).addItem(output);
        return CraftOutcome.success(
            "The blacksmith works your materials into a " + output.getName() + " for "
                + recipe.goldCost() + " gold. You now have " + updated.getGold() + " gold.",
            updated);
    }

    private Optional<Recipe> findRecipe(String normalized) {
        for (Recipe recipe : recipes) {
            if (recipe.id().value().equals(normalized)) {
                return Optional.of(recipe);
            }
        }
        for (Recipe recipe : recipes) {
            String outputName = resolveOutputName(recipe).toLowerCase(Locale.ROOT);
            if (outputName.equals(normalized) || outputName.startsWith(normalized)
                || recipe.id().value().startsWith(normalized)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    private int countInInventory(Player player, ItemId itemId) {
        int count = 0;
        for (Item item : player.getInventory()) {
            if (item.getId().equals(itemId)) {
                count++;
            }
        }
        return count;
    }

    private void consume(List<Item> inventory, ItemId itemId, int quantity) {
        int remaining = quantity;
        for (int i = 0; i < inventory.size() && remaining > 0; ) {
            if (inventory.get(i).getId().equals(itemId)) {
                inventory.remove(i);
                remaining--;
            } else {
                i++;
            }
        }
    }

    private String resolveOutputName(Recipe recipe) {
        return itemName(recipe.outputItemId(), recipe.name());
    }

    private String itemName(ItemId itemId) {
        return itemName(itemId, itemId.getValue());
    }

    private String itemName(ItemId itemId, String fallback) {
        try {
            return itemRepository.findById(itemId).map(Item::getName).orElse(fallback);
        } catch (RepositoryException e) {
            return fallback;
        }
    }
}
