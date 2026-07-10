package io.taanielo.jmud.core.enchant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.craft.RecipeMaterial;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.AffixId;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAffix;
import io.taanielo.jmud.core.world.ItemAffixService;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.AffixRepository;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Application service for enchanting: listing the Enchanter's known enchantments with live material
 * availability and permanently imbuing a carried, equippable item with an additional stat affix.
 *
 * <p>Unlike {@link io.taanielo.jmud.core.craft.CraftingService}, which produces a brand new output
 * item, enchanting mutates an existing inventory item <em>instance</em>: it appends an affix to that
 * specific {@link Item} (as a new immutable copy) and swaps it back into the player's inventory,
 * consuming the recipe's materials and gold. All operations are pure with respect to the
 * {@link Player}: the player passed in is never mutated; on success the caller receives an updated
 * copy inside {@link EnchantOutcome} which it applies on the tick thread (AGENTS.md §5). The service
 * is stateless apart from its immutable recipe table, so a single instance is safe to share.
 */
public class EnchantingService {

    /**
     * Maximum number of affixes an item may carry, matching how loot-generated items roll a single
     * affix. Enchanting an item already at this limit fails without consuming resources.
     */
    public static final int MAX_AFFIXES = 1;

    private final List<EnchantRecipe> recipes;
    private final ItemRepository itemRepository;
    private final AffixRepository affixRepository;
    private final ItemAffixService itemAffixService;

    /**
     * Creates an enchanting service over a fixed set of recipes.
     *
     * @param recipes          the known enchant recipes; copied defensively, may be empty
     * @param itemRepository   repository used to resolve material item names
     * @param affixRepository  repository used to resolve affix labels and stat bonuses
     * @param itemAffixService service used to compute an item's effective stats after enchanting
     */
    public EnchantingService(
        List<EnchantRecipe> recipes,
        ItemRepository itemRepository,
        AffixRepository affixRepository,
        ItemAffixService itemAffixService
    ) {
        this.recipes = List.copyOf(Objects.requireNonNull(recipes, "Recipes are required"));
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        this.affixRepository = Objects.requireNonNull(affixRepository, "Affix repository is required");
        this.itemAffixService = Objects.requireNonNull(itemAffixService, "Item affix service is required");
    }

    /**
     * Returns the known enchant recipes.
     *
     * @return an immutable list of recipes, possibly empty
     */
    public List<EnchantRecipe> recipes() {
        return recipes;
    }

    /**
     * Produces a formatted listing of every known enchantment, showing each affix, the stat bonus it
     * grants, its material requirements with the player's current {@code have X / need Y} counts, and
     * the gold cost.
     *
     * @param player the player whose inventory the have-counts are measured against
     * @return lines ready to be sent to the player's connection
     */
    public List<String> formatRecipes(Player player) {
        Objects.requireNonNull(player, "Player is required");
        List<String> lines = new ArrayList<>();
        if (recipes.isEmpty()) {
            lines.add("The enchanter has no enchantments to offer.");
            return lines;
        }
        lines.add("The enchanter can imbue the following "
            + "(ENCHANT <item> <enchantment> to apply one):");
        for (EnchantRecipe recipe : recipes) {
            List<String> parts = new ArrayList<>();
            for (RecipeMaterial material : recipe.materials()) {
                int have = countInInventory(player, material.itemId());
                parts.add(String.format("%s (have %d / need %d)",
                    itemName(material.itemId()), have, material.quantity()));
            }
            lines.add(String.format("  %s (%s) — %s; %d gold",
                affixLabel(recipe.affixId()), affixBonusSummary(recipe.affixId()),
                String.join(", ", parts), recipe.goldCost()));
        }
        return lines;
    }

    /**
     * Attempts to enchant a carried item with the affix named at the end of {@code input}.
     *
     * <p>The input is parsed as {@code <item> <enchantment>}: the trailing tokens are matched against
     * the known enchantments (by affix label or affix id, longest match wins) and the remaining
     * prefix names the inventory item to imbue. On success the recipe's materials and gold are
     * consumed and the affix is appended to that specific item instance. On any failure — unknown
     * enchantment, item not carried, item not equippable, item already at the affix limit, or
     * insufficient materials/gold — nothing is consumed.
     *
     * @param player the enchanting player
     * @param input  the {@code <item> <enchantment>} argument, or blank to prompt
     * @return the outcome describing success or the reason for failure
     */
    public EnchantOutcome enchant(Player player, @Nullable String input) {
        Objects.requireNonNull(player, "Player is required");
        if (input == null || input.isBlank()) {
            return EnchantOutcome.failure(
                "Enchant what? Type ENCHANT to see what the enchanter can imbue.");
        }
        String trimmed = input.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);

        EnchantRecipe matchedRecipe = null;
        String matchedSuffix = null;
        for (EnchantRecipe recipe : recipes) {
            for (String token : affixTokens(recipe)) {
                if (normalized.equals(token) || normalized.endsWith(" " + token)) {
                    if (matchedSuffix == null || token.length() > matchedSuffix.length()) {
                        matchedRecipe = recipe;
                        matchedSuffix = token;
                    }
                }
            }
        }
        if (matchedRecipe == null || matchedSuffix == null) {
            return EnchantOutcome.failure("The enchanter knows no enchantment matching '" + trimmed
                + "'. Type ENCHANT to list enchantments.");
        }
        String itemName = trimmed.substring(0, trimmed.length() - matchedSuffix.length()).trim();
        if (itemName.isEmpty()) {
            return EnchantOutcome.failure("Enchant which item? Usage: ENCHANT <item> <enchantment>.");
        }

        Item item = matchItem(player.getInventory(), itemName);
        if (item == null) {
            return EnchantOutcome.failure("You aren't carrying '" + itemName + "'.");
        }
        if (!item.isEquippable()) {
            return EnchantOutcome.failure("Only weapons and armour can be enchanted; "
                + item.getName() + " cannot bear a rune.");
        }
        if (item.getAffixes().size() >= MAX_AFFIXES) {
            return EnchantOutcome.failure(item.getName()
                + " already bears a rune of power and cannot be enchanted further.");
        }

        List<String> shortfalls = new ArrayList<>();
        for (RecipeMaterial material : matchedRecipe.materials()) {
            int have = countInInventory(player, material.itemId());
            if (have < material.quantity()) {
                shortfalls.add((material.quantity() - have) + " more " + itemName(material.itemId()));
            }
        }
        if (player.getGold() < matchedRecipe.goldCost()) {
            shortfalls.add((matchedRecipe.goldCost() - player.getGold()) + " more gold");
        }
        if (!shortfalls.isEmpty()) {
            return EnchantOutcome.failure("You cannot enchant " + item.getName()
                + " yet. You still need: " + String.join(", ", shortfalls) + ".");
        }

        Optional<ItemAffix> affixOpt;
        try {
            affixOpt = affixRepository.findById(matchedRecipe.affixId());
        } catch (RepositoryException e) {
            return EnchantOutcome.failure(
                "The enchanter fumbles the rune — the enchantment cannot be applied right now.");
        }
        if (affixOpt.isEmpty()) {
            return EnchantOutcome.failure(
                "The enchanter fumbles the rune — the enchantment cannot be applied right now.");
        }
        ItemAffix affix = affixOpt.get();

        Item enchanted = item.withAddedAffix(matchedRecipe.affixId());
        List<Item> inventory = new ArrayList<>(player.getInventory());
        int index = indexOf(inventory, item);
        if (index < 0) {
            return EnchantOutcome.failure("You aren't carrying '" + itemName + "'.");
        }
        inventory.set(index, enchanted);
        for (RecipeMaterial material : matchedRecipe.materials()) {
            consume(inventory, material.itemId(), material.quantity());
        }
        Player updated = player.withInventory(inventory).addGold(-matchedRecipe.goldCost());

        return EnchantOutcome.success(
            "The enchanter imbues " + item.getName() + " with " + affix.label() + " for "
                + matchedRecipe.goldCost() + " gold. It now grants: "
                + effectiveStatsSummary(enchanted) + ".",
            updated);
    }

    private List<String> affixTokens(EnchantRecipe recipe) {
        List<String> tokens = new ArrayList<>();
        tokens.add(recipe.affixId().getValue().toLowerCase(Locale.ROOT));
        tokens.add(affixLabel(recipe.affixId()).toLowerCase(Locale.ROOT));
        return tokens;
    }

    private String affixLabel(AffixId affixId) {
        try {
            return affixRepository.findById(affixId).map(ItemAffix::label).orElse(affixId.getValue());
        } catch (RepositoryException e) {
            return affixId.getValue();
        }
    }

    private String affixBonusSummary(AffixId affixId) {
        try {
            Optional<ItemAffix> affix = affixRepository.findById(affixId);
            if (affix.isEmpty()) {
                return "?";
            }
            return formatStats(affix.get().stats());
        } catch (RepositoryException e) {
            return "?";
        }
    }

    private String effectiveStatsSummary(Item item) {
        try {
            Map<String, Integer> stats = itemAffixService.effectiveStats(item);
            if (stats.isEmpty()) {
                return "no bonuses";
            }
            return formatStats(stats);
        } catch (RepositoryException e) {
            return "no bonuses";
        }
    }

    private String formatStats(Map<String, Integer> stats) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            int value = entry.getValue();
            parts.add(entry.getKey() + " " + (value >= 0 ? "+" : "") + value);
        }
        return String.join(", ", parts);
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

    private static int indexOf(List<Item> inventory, Item target) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i) == target) {
                return i;
            }
        }
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).equals(target)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static Item matchItem(List<Item> items, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Item item : items) {
            String name = item.getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return item;
            }
            String id = item.getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return item;
            }
        }
        return null;
    }

    private String itemName(ItemId itemId) {
        try {
            return itemRepository.findById(itemId).map(Item::getName).orElse(itemId.getValue());
        } catch (RepositoryException e) {
            return itemId.getValue();
        }
    }
}
