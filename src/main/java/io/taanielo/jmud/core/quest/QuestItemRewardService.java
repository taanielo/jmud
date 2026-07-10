package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Shared domain service that grants a quest's optional item reward on completion.
 *
 * <p>Every completion-granting quest service ({@link QuestKillService}, {@link QuestDeliveryService},
 * {@link QuestNpcDeliveryService}, {@link ExplorationQuestService} and {@link DailyQuestService})
 * delegates here so the "resolve the reward item, add it to the inventory, and handle a player who is
 * already at their carry-weight limit" logic lives in exactly one place rather than being duplicated
 * five times.
 *
 * <p>Copies of the reward item that would push the player over their carry weight are not silently
 * dropped from existence: they are surfaced as {@link ItemRewardGrant#droppedItems()} so the calling
 * (tick-thread) command handler can drop them at the player's feet in the current room, mirroring the
 * overweight-loot convention used elsewhere in the game. The service itself performs no room mutation
 * and no blocking I/O; it returns immutable {@link Player} snapshots (AGENTS.md §5).
 */
@Slf4j
public class QuestItemRewardService {

    private final ItemRepository itemRepository;
    private final EncumbranceService encumbranceService;

    /**
     * Creates an item-reward service backed by the given repositories.
     *
     * @param itemRepository     resolves reward item ids to {@link Item} templates; must not be null
     * @param encumbranceService determines whether granting an item would overburden the player;
     *                           must not be null
     */
    public QuestItemRewardService(ItemRepository itemRepository, EncumbranceService encumbranceService) {
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository is required");
        this.encumbranceService = Objects.requireNonNull(encumbranceService, "encumbranceService is required");
    }

    /**
     * Grants the item reward configured on {@code template} to {@code player}.
     *
     * <p>When the quest has no item reward, or the reward item id cannot be resolved, the player is
     * returned unchanged with an empty grant so quest completion is never blocked by a bad reference.
     * Copies that fit are added to the inventory; any copy that would overburden the player is placed
     * in {@link ItemRewardGrant#droppedItems()} for the caller to drop at the player's feet, and an
     * explanatory message is added to {@link ItemRewardGrant#messages()}.
     *
     * @param player   the player completing the quest (already carrying any gold/XP reward); must not be null
     * @param template the completed quest template; must not be null
     * @return the outcome bundling the updated player, a display description, dropped overflow items,
     *     and any overweight messages
     */
    public ItemRewardGrant grant(Player player, QuestTemplate template) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(template, "template is required");
        if (!template.hasItemReward()) {
            return ItemRewardGrant.none(player);
        }
        Item item = resolveItem(template.itemReward());
        if (item == null) {
            log.warn("Quest {} references unknown item reward '{}'; skipping item grant.",
                template.id().getValue(), template.itemReward());
            return ItemRewardGrant.none(player);
        }

        int quantity = Math.max(1, template.itemRewardQuantity());
        Player updated = player;
        List<Item> dropped = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            Player withItem = updated.addItem(item);
            if (encumbranceService.isOverburdened(withItem)) {
                dropped.add(item);
            } else {
                updated = withItem;
            }
        }

        List<String> messages = new ArrayList<>();
        if (!dropped.isEmpty()) {
            messages.add("You cannot carry " + describeQuantity(item.getName(), dropped.size())
                + ", so it falls to the ground at your feet.");
        }
        return new ItemRewardGrant(updated, describeQuantity(item.getName(), quantity), dropped, messages);
    }

    /**
     * Returns a short human-readable description of a quest's item reward for listing/status previews,
     * e.g. {@code "Troll Tooth Charm"} or {@code "2x Health Potion"}. Empty when the quest grants no
     * item reward or the item id cannot be resolved.
     *
     * @param template the quest template to describe; must not be null
     * @return the reward description, or empty
     */
    public Optional<String> describeReward(QuestTemplate template) {
        Objects.requireNonNull(template, "template is required");
        if (!template.hasItemReward()) {
            return Optional.empty();
        }
        Item item = resolveItem(template.itemReward());
        if (item == null) {
            return Optional.empty();
        }
        return Optional.of(describeQuantity(item.getName(), Math.max(1, template.itemRewardQuantity())));
    }

    /**
     * Builds the natural-language reward receipt sentence, weaving in the item reward when present.
     *
     * <p>Without an item reward this reads {@code "You receive 30 gold and 120 experience."}; with one
     * it reads {@code "You receive 30 gold, 120 experience, and Ranger's Cloak."}.
     *
     * @param gold            gold awarded
     * @param xp              experience awarded
     * @param itemDescription the item reward description, or {@code null} when none was granted
     * @return the completion receipt sentence
     */
    public static String receiveLine(int gold, int xp, String itemDescription) {
        if (itemDescription == null) {
            return "You receive " + gold + " gold and " + xp + " experience.";
        }
        return "You receive " + gold + " gold, " + xp + " experience, and " + itemDescription + ".";
    }

    private Item resolveItem(String itemId) {
        try {
            return itemRepository.findById(ItemId.of(itemId)).orElse(null);
        } catch (RepositoryException e) {
            log.warn("Failed to resolve quest reward item '{}': {}", itemId, e.getMessage());
            return null;
        }
    }

    private static String describeQuantity(String itemName, int quantity) {
        return quantity == 1 ? itemName : quantity + "x " + itemName;
    }

    /**
     * Outcome of granting a quest's item reward.
     *
     * @param player       the player with all fitting reward copies added to inventory
     * @param description  a display description of the reward (e.g. {@code "2x Health Potion"}), or
     *                     {@code null} when nothing was granted
     * @param droppedItems reward copies that did not fit and must be dropped at the player's feet by
     *                     the caller; never null, empty when everything fit
     * @param messages     player-facing messages describing overweight fallback; never null
     */
    public record ItemRewardGrant(
        Player player,
        String description,
        List<Item> droppedItems,
        List<String> messages
    ) {
        public ItemRewardGrant {
            Objects.requireNonNull(player, "player is required");
            droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        static ItemRewardGrant none(Player player) {
            return new ItemRewardGrant(player, null, List.of(), List.of());
        }

        /**
         * Returns the reward description, or empty when no item was granted.
         */
        public Optional<String> descriptionText() {
            return Optional.ofNullable(description);
        }
    }
}
