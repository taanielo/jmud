package io.taanielo.jmud.core.creation;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Domain service that grants a brand-new character its {@link NewbieKit starting kit} — a small gold
 * purse and a few provisions — when character creation completes.
 *
 * <p>The kit exists to bootstrap the early-game economy: hunger and thirst decay every tick and
 * their only cure (food/water items) costs gold, so a fresh character with zero gold and an empty
 * inventory would be broke, hungry and thirsty from minute one. The concrete values are data-driven
 * ({@code data/newbie-kit.json}); this service only applies them.
 *
 * <p>Application is a pure, deterministic transform on the {@link Player} value object, so it runs
 * safely on the tick thread and is fully unit-testable without networking (AGENTS.md §5, §10). It is
 * only ever applied at creation time, so existing saved characters are never re-kitted.
 *
 * <p>This class is stateless (beyond its immutable kit and repository) and thread-safe; one instance
 * may be shared across all connected clients.
 */
@Slf4j
public class NewbieKitService {

    private final NewbieKit kit;
    private final ItemRepository itemRepository;

    /**
     * Constructs the service with the kit definition and the repository used to resolve item ids.
     *
     * @param kit            the starting-kit definition to grant
     * @param itemRepository repository used to resolve starting item ids to concrete items
     */
    public NewbieKitService(NewbieKit kit, ItemRepository itemRepository) {
        this.kit = Objects.requireNonNull(kit, "Newbie kit is required");
        this.itemRepository = Objects.requireNonNull(itemRepository, "Item repository is required");
    }

    /**
     * Grants the starting kit to a freshly created player: adds the starting gold and each starting
     * item to the player's inventory.
     *
     * <p>An item id that cannot be resolved (missing data file or repository error) is logged and
     * skipped rather than aborting creation, so a single bad entry never leaves a new player stuck in
     * a half-created state.
     *
     * @param player the newly created player to equip; must not be {@code null}
     * @return the player with the starting gold and items added
     */
    public Player applyTo(Player player) {
        Objects.requireNonNull(player, "Player is required");
        Player result = player;
        if (kit.startingGold() > 0) {
            result = result.addGold(kit.startingGold());
        }
        for (ItemId itemId : kit.itemIds()) {
            Item item = resolve(itemId);
            if (item != null) {
                result = result.addItem(item);
            }
        }
        return result;
    }

    private Item resolve(ItemId itemId) {
        try {
            return itemRepository.findById(itemId)
                .orElseGet(() -> {
                    log.warn("Newbie kit references unknown item '{}'; skipping.", itemId.getValue());
                    return null;
                });
        } catch (RepositoryException e) {
            log.warn("Failed to load newbie kit item '{}': {}", itemId.getValue(), e.getMessage());
            return null;
        }
    }
}
