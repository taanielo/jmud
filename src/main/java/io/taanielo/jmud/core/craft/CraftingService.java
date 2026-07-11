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
    private final CrafterProfile profile;

    /**
     * Creates a crafting service over a fixed set of recipes backed by the default blacksmith
     * profile (the {@code CRAFT} command).
     *
     * @param recipes        the known recipes; copied defensively, may be empty
     * @param itemRepository repository used to resolve material and output item definitions
     */
    public CraftingService(List<Recipe> recipes, ItemRepository itemRepository) {
        this(recipes, itemRepository, CrafterProfile.blacksmith());
    }

    /**
     * Creates a crafting service over a fixed set of recipes backed by a specific crafter profile,
     * allowing the same logic to serve both {@code CRAFT} (blacksmith) and {@code BREW} (alchemist).
     *
     * @param recipes        the known recipes; copied defensively, may be empty
     * @param itemRepository repository used to resolve material and output item definitions
     * @param profile        the crafter profile controlling player-facing wording
     */
    public CraftingService(List<Recipe> recipes, ItemRepository itemRepository, CrafterProfile profile) {
        this.recipes = List.copyOf(Objects.requireNonNull(recipes, "Recipes are required"));
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        this.profile = Objects.requireNonNull(profile, "Crafter profile is required");
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
            lines.add("The " + profile.crafter() + " has no recipes to offer.");
            return lines;
        }
        int playerLevel = player.proficiencies().level(profile.profession());
        lines.add("The " + profile.crafter() + " can " + profile.verb() + " the following ("
            + profile.command() + " <item> to make one) [your " + profile.profession().value()
            + " level: " + playerLevel + "]:");
        for (Recipe recipe : recipes) {
            List<String> parts = new ArrayList<>();
            for (RecipeMaterial material : recipe.materials()) {
                int have = countInInventory(player, material.itemId());
                parts.add(String.format("%s (have %d / need %d)",
                    itemName(material.itemId()), have, material.quantity()));
            }
            String lockTag = recipe.minSkill() > playerLevel
                ? " [requires " + profile.profession().value() + " " + recipe.minSkill() + "]"
                : "";
            lines.add(String.format("  %s — %s; %d gold%s",
                resolveOutputName(recipe), String.join(", ", parts), recipe.goldCost(), lockTag));
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
            return CraftOutcome.failure(profile.capitalizedVerb() + " what? Type " + profile.command()
                + " to see what the " + profile.crafter() + " can make.");
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        Optional<Recipe> recipeOpt = findRecipe(normalized);
        if (recipeOpt.isEmpty()) {
            return CraftOutcome.failure(
                "The " + profile.crafter() + " knows no recipe for '" + input.trim() + "'.");
        }
        Recipe recipe = recipeOpt.get();

        int playerLevel = player.proficiencies().level(profile.profession());
        if (recipe.minSkill() > playerLevel) {
            return CraftOutcome.failure(
                "Your " + profile.profession().value() + " skill is too low to " + profile.verb()
                    + " the " + resolveOutputName(recipe) + ". It requires "
                    + profile.profession().value() + " level " + recipe.minSkill()
                    + ", but you are only level " + playerLevel + ".");
        }

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
                "You cannot " + profile.verb() + " the " + resolveOutputName(recipe)
                    + " yet. You still need: " + String.join(", ", shortfalls) + ".");
        }

        Item output;
        try {
            Optional<Item> outputOpt = itemRepository.findById(recipe.outputItemId());
            if (outputOpt.isEmpty()) {
                return CraftOutcome.failure(
                    "The " + profile.crafter() + " fumbles — the " + resolveOutputName(recipe)
                        + " cannot be made right now.");
            }
            output = outputOpt.get();
        } catch (RepositoryException e) {
            return CraftOutcome.failure(
                "The " + profile.crafter() + " fumbles — the " + resolveOutputName(recipe)
                    + " cannot be made right now.");
        }

        List<Item> inventory = new ArrayList<>(player.getInventory());
        for (RecipeMaterial material : recipe.materials()) {
            consume(inventory, material.itemId(), material.quantity());
        }
        PlayerProficiencies grown =
            player.proficiencies().gain(profile.profession(), recipe.proficiencyGain());
        int newLevel = grown.level(profile.profession());
        Player updated = player.withInventory(inventory)
            .addGold(-recipe.goldCost())
            .addItem(output)
            .withProficiencies(grown);
        StringBuilder message = new StringBuilder("The ").append(profile.crafter()).append(' ')
            .append(profile.craftAction()).append(" a ").append(output.getName())
            .append(" for ").append(recipe.goldCost()).append(" gold. You now have ")
            .append(updated.getGold()).append(" gold.");
        if (newLevel > playerLevel) {
            message.append(" Your ").append(profile.profession().value())
                .append(" proficiency rises to level ").append(newLevel).append('!');
        }
        return CraftOutcome.success(message.toString(), updated);
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
