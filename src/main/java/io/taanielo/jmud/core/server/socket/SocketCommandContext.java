package io.taanielo.jmud.core.server.socket;

import java.util.List;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.player.Player;

/**
 * Command execution context for socket clients, providing safe access to actions and state.
 */
public interface SocketCommandContext extends Client {

    /**
     * Returns whether the client is authenticated.
     */
    boolean isAuthenticated();

    /**
     * Returns the current player or null when unauthenticated.
     */
    Player getPlayer();

    /**
     * Returns the list of connected clients.
     */
    List<io.taanielo.jmud.core.server.Client> clients();

    /**
     * Returns the usernames of all connected, authenticated players.
     *
     * <p>Clients that are connected but not yet authenticated (still at login)
     * are excluded. The result is a read-only snapshot intended for the
     * {@code who} command.
     */
    List<Username> onlinePlayerNames();

    /**
     * Sends a look response for the current player.
     */
    void sendLook();

    /**
     * Sends a look response for a target player in the same room.
     */
    void sendLookAt(String targetInput);

    /**
     * Attempts to move the player in the given direction.
     */
    void sendMove(Direction direction);

    /**
     * Executes an ability command using the provided arguments.
     */
    void useAbility(String args);

    /**
     * Casts a spell-type ability using the provided arguments.
     *
     * <p>Unlike {@link #useAbility(String)}, this method restricts activation to
     * abilities whose {@link io.taanielo.jmud.core.ability.AbilityType} is
     * {@code SPELL}. Using it on a skill-type ability prints an error message.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the spell name and optional target (e.g. {@code "fireball goblin"})
     */
    default void castSpell(String args) {}

    /**
     * Updates ANSI settings using the provided arguments.
     */
    void updateAnsi(String args);

    /**
     * Sends a line and prompt to the player.
     */
    void writeLineWithPrompt(String message);

    /**
     * Sends a line without a prompt to the player.
     */
    void writeLineSafe(String message);

    /**
     * Sends just the prompt to the player.
     */
    void sendPrompt();

    /**
     * Sends a message to a specific connected username.
     */
    void sendToUsername(Username username, String message);

    /**
     * Sends a message to other occupants in the room.
     */
    void sendToRoom(Player source, Player target, String message);

    /**
     * Sends a message to other occupants in the room, excluding the source.
     */
    void sendToRoom(Player source, String message);

    /**
     * Resolves a target in the same room by input.
     */
    java.util.Optional<Player> resolveTarget(Player source, String input);

    /**
     * Executes an attack command with the provided arguments.
     */
    void executeAttack(String args);

    /**
     * Executes a get command with the provided arguments.
     */
    void getItem(String args);

    /**
     * Executes a drop command with the provided arguments.
     */
    void dropItem(String args);

    /**
     * Executes a quaff command with the provided arguments.
     */
    void quaffItem(String args);

    /**
     * Executes an equip command with the provided arguments.
     */
    void equipItem(String args);

    /**
     * Executes an unequip command with the provided arguments.
     */
    void unequipItem(String args);

    /**
     * Executes a kill command targeting a mob in the same room.
     */
    void killMob(String args);

    /**
     * Sends the current player's inventory listing (carried items and encumbrance).
     */
    void sendInventory();

    /**
     * Sends the current player's equipment listing (worn items per slot).
     */
    void sendEquipment();

    /**
     * Examines an item by name, searching the player's inventory then the current room.
     */
    default void examineItem(String args) {}

    /**
     * Sends the player's known abilities as a formatted table.
     *
     * <p>Each line shows the ability name, its type (SKILL/SPELL), resource cost,
     * and cooldown in ticks.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     */
    default void sendAbilities() {}

    /**
     * Attempts to flee from active combat by moving to a random available exit.
     */
    void fleeCombat();

    /**
     * Assesses the danger of a mob in the same room and prints a qualitative tier message.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the mob name (prefix matching supported)
     */
    default void considerMob(String args) {}

    /**
     * Broadcasts a gossip message from the named sender to all online players.
     *
     * <p>Each recipient other than the sender receives:
     * {@code <senderName> gossips: <message>}
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param senderName the display name of the gossiping player
     * @param message    the message text to broadcast
     */
    default void gossip(String senderName, String message) {}

    /**
     * Puts the player into a resting state and starts the regen ticker.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     */
    default void startResting() {}

    /**
     * Cancels resting if the player is currently resting, sending the given
     * wake message to the player.
     *
     * <p>If the player is not resting this method is a no-op.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param message the message shown to the player on wake; ignored when not resting
     */
    default void stopResting(String message) {}

    /**
     * Lists the inventory of the shop in the current room.
     *
     * <p>Prints an error message when there is no shop here.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     */
    default void listShopInventory() {}

    /**
     * Buys the named item from the shop in the current room.
     *
     * <p>Deducts the item's price from the player's gold and places the item
     * in the player's inventory. Prints an error if the item is unknown or
     * the player cannot afford it.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the item name to buy
     */
    default void buyFromShop(String args) {}

    /**
     * Sells the named item from the player's inventory to the shop in the current room.
     *
     * <p>Removes the item from inventory and adds gold at the shop's sell ratio.
     * Prints an error if the item is not in inventory.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the item name to sell
     */
    default void sellToShop(String args) {}

    /**
     * Executes a QUEST sub-command (LIST, ACCEPT, STATUS, COMPLETE, ABANDON).
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "ACCEPT rat-catcher"})
     */
    default void executeQuest(String args) {}

    /**
     * Executes a TRAIN sub-command (LIST or an ability id).
     *
     * <p>The command requires the player to be in the Training Yard with the
     * Master Trainer present. {@code TRAIN LIST} shows trainable abilities for
     * the player's class. {@code TRAIN <id>} spends one practice point to learn
     * the named ability.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the sub-command or ability id to train (e.g. {@code "LIST"} or {@code "skill.bash"})
     */
    default void executeTrain(String args) {}

    /**
     * Executes a PARTY sub-command (FORM, INVITE, ACCEPT, DECLINE, LEAVE, DISBAND, or empty).
     *
     * <p>Manages in-memory party groups that share XP on mob kills and display
     * member HP in the prompt via the {@code {partyHp}} token.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "INVITE Alice"})
     */
    default void executeParty(String args) {}

    /**
     * Locks the door in the given direction from the player's current room.
     *
     * <p>Requires the player to carry the correct key item. Prints a failure message
     * when the exit is not lockable or the player lacks the key.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param direction the direction of the exit to lock
     */
    default void lockExit(Direction direction) {}

    /**
     * Unlocks the door in the given direction from the player's current room.
     *
     * <p>Requires the player to carry the correct key item. Prints a failure message
     * when the exit is not lockable, already unlocked, or the player lacks the key.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param direction the direction of the exit to unlock
     */
    default void unlockExit(Direction direction) {}

}
