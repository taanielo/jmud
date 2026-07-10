package io.taanielo.jmud.core.salvage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Application service for salvaging: breaking down unwanted equippable gear back into crafting
 * materials at a blacksmith. It gives low-value loot a second use and offers crafters a skill-free
 * (but worse than gathering) source of materials.
 *
 * <p>All operations are pure with respect to the {@link Player}: the player passed in is never
 * mutated. On a successful salvage the caller receives an updated player copy inside the returned
 * {@link SalvageOutcome}, which it applies on the tick thread (AGENTS.md §5). The service is stateless
 * apart from its immutable rarity-to-yield table, so a single instance is safe to share.
 */
public class SalvageService {

    private final Map<Rarity, SalvageTier> tiersByRarity;
    private final ItemRepository itemRepository;

    /**
     * Creates a salvage service over a fixed set of rarity tiers.
     *
     * @param tiers          the salvage yield per rarity tier; copied defensively, at most one entry
     *                       per rarity
     * @param itemRepository repository used to resolve yielded material item definitions
     * @throws IllegalArgumentException if two tiers target the same rarity
     */
    public SalvageService(List<SalvageTier> tiers, ItemRepository itemRepository) {
        Objects.requireNonNull(tiers, "Salvage tiers are required");
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
        Map<Rarity, SalvageTier> byRarity = new EnumMap<>(Rarity.class);
        for (SalvageTier tier : tiers) {
            if (byRarity.putIfAbsent(tier.rarity(), tier) != null) {
                throw new IllegalArgumentException(
                    "Duplicate salvage tier for rarity '" + tier.rarity().id() + "'");
            }
        }
        this.tiersByRarity = byRarity;
    }

    /**
     * Produces a formatted listing of every salvageable item the player is carrying, showing the
     * material(s) and quantity each would yield. Equipped items and non-equippable items (potions,
     * food, currency, keys, quest items) are excluded.
     *
     * @param player the player whose inventory is inspected
     * @return lines ready to be sent to the player's connection
     */
    public List<String> preview(Player player) {
        Objects.requireNonNull(player, "Player is required");
        List<String> lines = new ArrayList<>();
        List<Item> salvageable = new ArrayList<>();
        for (Item item : player.getInventory()) {
            if (isSalvageable(player, item)) {
                salvageable.add(item);
            }
        }
        if (salvageable.isEmpty()) {
            lines.add("You have nothing worth salvaging. Only unequipped weapons and armor can be "
                + "broken down.");
            return lines;
        }
        lines.add("You could salvage the following (SALVAGE <item> to break one down):");
        for (Item item : salvageable) {
            Optional<SalvageTier> tier = resolveTier(item.getRarity());
            String yield = tier.map(this::describeYield).orElse("nothing");
            lines.add(String.format("  %s (%s) — %s", item.getName(), item.getRarity().id(), yield));
        }
        return lines;
    }

    /**
     * Attempts to salvage the named carried item on behalf of the player.
     *
     * <p>On success the item is removed and the material(s) for its rarity tier are added to the
     * player's inventory. The salvage fails, changing nothing, when the argument is blank, the item is
     * not carried, the item is currently equipped, the item is not equippable gear (a quest item,
     * consumable, currency or key), or no yield is configured for its rarity tier.
     *
     * @param player the salvaging player
     * @param input  the item name (or prefix) to salvage
     * @return the outcome describing success or the reason for failure
     */
    public SalvageOutcome salvage(Player player, @Nullable String input) {
        Objects.requireNonNull(player, "Player is required");
        if (input == null || input.isBlank()) {
            return SalvageOutcome.failure("Salvage what? Type SALVAGE to see what you can break down.");
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        Optional<Item> found = findCarried(player, normalized);
        if (found.isEmpty()) {
            return SalvageOutcome.failure("You are not carrying '" + input.trim() + "'.");
        }
        Item item = found.get();
        if (player.getEquipment().isEquipped(item.getId())) {
            return SalvageOutcome.failure(
                "You must UNEQUIP the " + item.getName() + " before you can salvage it.");
        }
        if (!item.isEquippable()) {
            return SalvageOutcome.failure(
                "The " + item.getName() + " cannot be salvaged; only weapons and armor can be broken down.");
        }
        Optional<SalvageTier> tierOpt = resolveTier(item.getRarity());
        if (tierOpt.isEmpty()) {
            return SalvageOutcome.failure(
                "The blacksmith cannot break down the " + item.getName() + " right now.");
        }
        SalvageTier tier = tierOpt.get();

        List<Item> materials = new ArrayList<>();
        for (SalvageMaterial yield : tier.materials()) {
            Optional<Item> material = lookupItem(yield.itemId());
            if (material.isEmpty()) {
                return SalvageOutcome.failure(
                    "The blacksmith cannot break down the " + item.getName() + " right now.");
            }
            for (int i = 0; i < yield.quantity(); i++) {
                materials.add(material.get());
            }
        }

        Player updated = player.removeItem(item);
        for (Item material : materials) {
            updated = updated.addItem(material);
        }
        return SalvageOutcome.success(
            "You salvage the " + item.getName() + ", recovering " + describeYield(tier) + ".", updated);
    }

    private boolean isSalvageable(Player player, Item item) {
        return item.isEquippable()
            && !player.getEquipment().isEquipped(item.getId())
            && resolveTier(item.getRarity()).isPresent();
    }

    private Optional<Item> findCarried(Player player, String normalized) {
        for (Item item : player.getInventory()) {
            String name = item.getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return Optional.of(item);
            }
            String id = item.getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private Optional<SalvageTier> resolveTier(Rarity rarity) {
        return Optional.ofNullable(tiersByRarity.get(rarity));
    }

    private String describeYield(SalvageTier tier) {
        List<String> parts = new ArrayList<>();
        for (SalvageMaterial material : tier.materials()) {
            parts.add(material.quantity() + " " + itemName(material.itemId()));
        }
        return String.join(", ", parts);
    }

    private Optional<Item> lookupItem(ItemId itemId) {
        try {
            return itemRepository.findById(itemId);
        } catch (RepositoryException e) {
            return Optional.empty();
        }
    }

    private String itemName(ItemId itemId) {
        return lookupItem(itemId).map(Item::getName).orElse(itemId.getValue());
    }
}
