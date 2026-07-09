package io.taanielo.jmud.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Domain service for NPC-delivery quests: a giver NPC hands the player a package which must be
 * carried to a receiver NPC standing in another zone. Completing the delivery grants gold, XP and
 * an optional title.
 *
 * <p>Progress needs no separate counter — holding the package item and reaching the receiver NPC's
 * room is the whole quest. The service is stateless and thread-safe; all mutation is expressed as
 * new immutable {@link Player} snapshots returned to the (tick-thread) caller.
 */
@Slf4j
public class QuestNpcDeliveryService {

    private final QuestRepository questRepository;
    private final LevelUpService levelUpService;

    public QuestNpcDeliveryService(QuestRepository questRepository) {
        this.questRepository = Objects.requireNonNull(questRepository, "questRepository is required");
        this.levelUpService = new LevelUpService();
    }

    /**
     * Activates an NPC-delivery quest for the player, handing over the package item.
     *
     * <p>The caller is responsible for confirming the player has no other active quest and for
     * resolving the {@code packageItem} from the item repository. Fails loudly when the template is
     * not an NPC-delivery quest or the supplied item does not match {@code packageItemId}.
     *
     * @param player      the player accepting the quest; must not be null
     * @param template    the NPC-delivery quest template; must not be null
     * @param packageItem the package item to hand over; must not be null
     * @return a {@link DeliveryQuestResult} with the updated player on success
     */
    public DeliveryQuestResult grant(Player player, QuestTemplate template, Item packageItem) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(template, "template is required");
        Objects.requireNonNull(packageItem, "packageItem is required");

        if (!template.isNpcDeliveryQuest()) {
            return DeliveryQuestResult.failure("That contract is not a delivery errand.");
        }
        if (player.getActiveQuest() != null) {
            return DeliveryQuestResult.failure("You already hold an active contract. Abandon it first.");
        }
        if (!packageItem.getId().getValue().equalsIgnoreCase(template.packageItemId())) {
            return DeliveryQuestResult.failure("The package does not match this errand.");
        }

        Player updated = player
            .withActiveQuest(new ActiveQuest(template.id(), 0))
            .addItem(packageItem);

        List<String> messages = new ArrayList<>();
        messages.add("You accept the errand: " + template.name() + ".");
        messages.add("You receive " + packageItem.getName()
            + ". Deliver it to " + template.receiverNpcId() + ".");
        return DeliveryQuestResult.success(updated, messages);
    }

    /**
     * Attempts to hand the package to the receiver NPC for a reward.
     *
     * <p>Succeeds only when the player has an active NPC-delivery quest, is standing in the
     * receiver's room, the receiver NPC is present, and the player still holds the package item. On
     * success the package is consumed, gold and XP are granted (level-ups included in the messages),
     * any title reward is awarded, and the active quest is cleared.
     *
     * @param player          the player attempting delivery; must not be null
     * @param currentRoom     the room the player is currently in; must not be null
     * @param receiverPresent whether the receiver NPC is present in the current room
     * @return a {@link DeliveryQuestResult} describing the outcome
     */
    public DeliveryQuestResult deliver(Player player, RoomId currentRoom, boolean receiverPresent) {
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(currentRoom, "currentRoom is required");

        ActiveQuest active = player.getActiveQuest();
        if (active == null) {
            return DeliveryQuestResult.failure("You have no package to deliver.");
        }

        QuestTemplate template;
        try {
            template = questRepository.findById(active.templateId()).orElse(null);
        } catch (QuestRepositoryException e) {
            log.warn("Failed to load quest template on deliver {}: {}", active.templateId(), e.getMessage());
            return DeliveryQuestResult.failure("Quest data unavailable. Try again.");
        }

        if (template == null) {
            return DeliveryQuestResult.failure("Unknown quest template. Use QUEST ABANDON to clear it.");
        }
        if (!template.isNpcDeliveryQuest()) {
            return DeliveryQuestResult.failure(
                "That contract is not a package errand. Use QUEST COMPLETE or QUEST DELIVER instead.");
        }
        if (!currentRoom.getValue().equalsIgnoreCase(template.receiverRoomId()) || !receiverPresent) {
            return DeliveryQuestResult.failure(
                "The recipient is not here. Seek out " + template.receiverNpcId() + ".");
        }
        if (!holdsPackage(player, template.packageItemId())) {
            return DeliveryQuestResult.failure(
                "You no longer carry the package. The errand cannot be completed.");
        }

        Player updated = removePackage(player, template.packageItemId())
            .withActiveQuest(null)
            .addGold(template.goldReward());

        LevelUpService.LevelUpResult lvResult = levelUpService.awardXp(updated, template.xpReward());
        updated = lvResult.player();

        List<String> messages = new ArrayList<>();
        messages.add("You hand over the package. Errand complete: " + template.name() + ".");
        messages.add("You receive " + template.goldReward() + " gold and "
            + template.xpReward() + " experience.");
        if (lvResult.leveledUp()) {
            messages.add("You have advanced to level " + updated.getLevel() + "!");
        }
        String titleReward = template.titleReward();
        if (titleReward != null && !updated.titles().has(titleReward)) {
            updated = updated.grantTitle(titleReward);
            messages.add("You have earned the title: " + titleReward + "!");
        }
        return DeliveryQuestResult.success(updated, messages);
    }

    // ── private helpers ────────────────────────────────────────────────

    private boolean holdsPackage(Player player, String packageItemId) {
        for (Item item : player.getInventory()) {
            if (item.getId().getValue().equalsIgnoreCase(packageItemId)) {
                return true;
            }
        }
        return false;
    }

    private Player removePackage(Player player, String packageItemId) {
        List<Item> remaining = new ArrayList<>(player.getInventory().size());
        boolean removed = false;
        for (Item item : player.getInventory()) {
            if (!removed && item.getId().getValue().equalsIgnoreCase(packageItemId)) {
                removed = true;
                continue;
            }
            remaining.add(item);
        }
        return player.withInventory(remaining);
    }
}
