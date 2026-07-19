package io.taanielo.jmud.core.server.socket;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

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
     * Handles the {@code AUTOLOOT} command: shows the current setting with no arguments or
     * {@code STATUS}, and toggles it with {@code ON}, {@code OFF}, or {@code TOGGLE}.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     */
    default void updateAutoLoot(String args) {}

    /**
     * Handles the {@code AUTOASSIST} command: shows the current setting with no arguments or
     * {@code STATUS}, and toggles it with {@code ON}, {@code OFF}, or {@code TOGGLE}. When enabled,
     * the player is automatically joined into a party-mate's fight against a fresh mob.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     */
    default void updateAutoAssist(String args) {}

    /**
     * Handles the {@code BRIEF} command: shows the current setting with no arguments or
     * {@code STATUS}, and toggles it with {@code ON}, {@code OFF}, or {@code TOGGLE}. When enabled,
     * movement omits the room's prose description line; explicit {@code LOOK} always shows it.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     */
    default void updateBrief(String args) {}

    /**
     * Handles the {@code PROMPT} command: shows the current prompt format with no arguments,
     * updates and persists it with {@code SET <format>}, or toggles prompt colorization with
     * {@code COLOR [on|off|toggle|status]}.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "SET [%h/%Hhp] >"})
     */
    default void updatePrompt(String args) {}

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
     * Returns the live in-session {@link Player} for the given connected username, or
     * {@code null} when no client is currently authenticated as that user.
     *
     * <p>Used to inspect another online player's state (e.g. their ignore list) on the tick
     * thread. The default implementation returns {@code null} so existing test stubs do not
     * need to be updated.
     *
     * @param username the username to resolve
     */
    default Player getOnlinePlayer(Username username) {
        return null;
    }

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
     * Broadcasts a {@code SAY} message to the source's room, excluding the source and any
     * occupant who is ignoring the source (issue #339). Ignored recipients see nothing and the
     * source is never told they were ignored.
     *
     * <p>The default implementation delegates to {@link #sendToRoom(Player, String)} (no
     * filtering) so existing test stubs do not need to be updated.
     *
     * @param source  the speaking player
     * @param message the fully-rendered room message
     */
    default void sendRoomSay(Player source, String message) {
        sendToRoom(source, message);
    }

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
     * Picks up every item currently on the room floor in one command (the {@code GET ALL} form).
     *
     * <p>Mirrors {@link #getItem(String)}'s per-item side effects — delivery-quest pickup progress and
     * the dark-room light-reveal check — applying them across every newly gathered item. Stops early
     * and keeps prior pickups if the player becomes overburdened partway through.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void getAllItems() {}

    /**
     * Retrieves every item from a carried container into the player's inventory in one command (the
     * {@code GET ALL FROM <container>} form).
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param containerInput the container name or id to empty
     */
    default void getAllFromContainer(String containerInput) {}

    /**
     * Drops every unequipped inventory item to the room floor in one command (the {@code DROP ALL}
     * form). Worn or wielded gear is left alone and never auto-unequipped.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void dropAllItems() {}

    /**
     * Places an item from the player's inventory into a carried container.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     *
     * @param itemInput      the item name or id to place inside the container
     * @param containerInput the container name or id to place the item into
     */
    default void putIntoContainer(String itemInput, String containerInput) {}

    /**
     * Retrieves an item from a carried container into the player's inventory.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     *
     * @param itemInput      the item name or id to retrieve from the container
     * @param containerInput the container name or id to retrieve the item from
     */
    default void getFromContainer(String itemInput, String containerInput) {}

    /**
     * Gives an item from the player's inventory to an already-resolved target player.
     *
     * <p>The caller ({@link GiveCommand}) is responsible for confirming the target is online
     * and currently in the same room before calling this method; this method resolves the
     * target's live in-session state, hands off to {@code GameActionService.giveItem}, and
     * delivers the resulting messages/state updates. Fails with an explanatory message if the
     * named item is not in the giver's inventory, or if the recipient would become overburdened.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     *
     * @param targetUsername the recipient, already confirmed online and in the same room
     * @param itemInput the item name or id to give
     */
    default void giveItem(Username targetUsername, String itemInput) {}

    /**
     * Executes a quaff command with the provided arguments.
     */
    void quaffItem(String args);

    /**
     * Executes an eat command with the provided arguments. Default is a no-op; the
     * socket-backed implementation overrides it to apply hunger satisfaction.
     */
    default void eatItem(String args) {}

    /**
     * Executes a drink command with the provided arguments. Default is a no-op; the
     * socket-backed implementation overrides it to apply thirst satisfaction.
     */
    default void drinkItem(String args) {}

    /**
     * Executes a read command with the provided arguments.
     */
    void readItem(String args);

    /**
     * Executes an identify command with the provided arguments, revealing a carried item's rarity
     * tier and affixes. Defaults to a no-op for non-socket implementations; the socket-backed
     * implementation overrides it.
     */
    default void identifyItem(String args) {}

    /**
     * Executes a write command with the provided arguments.
     */
    void writeItem(String args);

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
     * Executes an ASSIST command: engages the caller in combat against the mob that the named player
     * in the same room is currently fighting, using the same per-tick auto-attack engagement as
     * {@link #killMob(String)}.
     *
     * <p>Breaks stealth and dismounts as a side effect, consistent with initiating combat via
     * {@code KILL}. Fails with a clear message when the named player is not present in the room or is
     * not currently engaged with a live mob.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the name of the player to assist
     */
    default void executeAssist(String args) {}

    /**
     * Executes a TAUNT command: the Warrior-only skill that forces a mob already in combat in the
     * caller's room to prioritise attacking the caller for the next few AI decisions, peeling it off
     * an ally.
     *
     * <p>Fails with a clear message when the caller has not learned the skill (including non-Warriors),
     * the skill is on cooldown, no matching mob is present, or the named mob is not currently engaged
     * in combat. Costs move points on success and starts the skill's cooldown.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the name of the mob to taunt
     */
    default void executeTaunt(String args) {}

    /**
     * Executes a SHOOT command: fires a ranged weapon at a mob in an adjacent room.
     *
     * <p>Parses {@code args} as {@code <target> <direction>}, verifies the player wields a ranged
     * weapon, and resolves the attack against the named mob in the room lying in the given
     * direction. Fails with a clear message when the direction is missing/invalid, no matching mob
     * is in that room, or the player is not wielding a ranged weapon.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the raw command arguments ({@code <target> <direction>})
     */
    default void executeRangedAttack(String args) {}

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
     * Compares an item (matched the same way {@link #examineItem(String)} matches: inventory first,
     * then the current room floor, with partial name matching) against whatever the player currently
     * has equipped in that item's slot, printing a per-stat delta plus weight, value, and durability.
     *
     * <p>Explains why it cannot compare when the matched item is unidentified or not equippable, and
     * notes an empty target slot rather than diffing against nothing.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the item name to compare (partial match supported)
     */
    default void compareItem(String args) {}

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
     * Sends the current player's active status effects, split into beneficial and harmful groups,
     * with each effect's display name, remaining duration in ticks (or {@code permanent}), and
     * stack count when stacked. Prints a clear "no active effects" line when the player has none.
     *
     * <p>This is a pure read of the player's live effect list (the {@code EFFECTS}/{@code AFFECTS}
     * command); it never mutates effect durations or stacks.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     */
    default void sendEffects() {}

    /**
     * Sends the player's learned abilities as a formatted table with each ability's live
     * cooldown status.
     *
     * <p>Each line shows the ability name, its type (SKILL/SPELL), and its current readiness:
     * {@code Ready} when off cooldown, or {@code <n> ticks} remaining otherwise. Status is read
     * from the player's own {@code CooldownSystem}; this is a pure read that never mutates
     * cooldown state. Prints a clear "no abilities learned" line when the player has none.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     */
    default void sendCooldowns() {}

    /**
     * Attempts to flee from active combat by moving to a random available exit.
     */
    void fleeCombat();

    /**
     * Attempts to teleport the player back to the starting/town room.
     *
     * <p>Blocked while the player is in active combat (use FLEE first) or while recall is on
     * cooldown; on success, departure and arrival messages are broadcast to the old and new
     * rooms respectively.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     */
    default void recall() {}

    /**
     * Handles the {@code BIND} command: with no argument reports the player's current recall/respawn
     * anchor; with any argument (e.g. {@code HERE}) anchors it to the waypoint room of the player's
     * current area, if they are standing in one and out of combat.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the raw argument after {@code BIND} (blank means "report current bind point")
     */
    default void bind(String args) {}

    /**
     * Handles the {@code WAYFIND} command: with no argument lists the player's current area and its
     * charted neighbours; with an area name or id it prints the shortest walking route (turn-by-turn
     * compass directions plus a step count) to that area's waypoint room, or a friendly message when
     * the area is unknown, ambiguous, or unreachable on foot.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the raw argument after {@code WAYFIND} (blank means "list nearby areas")
     */
    default void wayfind(String args) {}

    /**
     * Handles the {@code AUTOWALK} command: resolves the destination area exactly like {@code WAYFIND}
     * and, when the shortest route is pure walking, begins auto-walking one room per tick toward that
     * area's waypoint. {@code AUTOWALK STOP} cancels an in-progress walk; a route requiring the ferry
     * is refused with a pointer to {@code WAYFIND}. The walk auto-cancels on any manual command, on
     * entering combat, on a blocked step, and on arrival.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the raw argument after {@code AUTOWALK} (an area name/id, or {@code STOP})
     */
    default void autoWalk(String args) {}

    /**
     * Cancels any in-progress {@code AUTOWALK} for the caller, printing a one-line notice. Invoked by
     * {@link SocketCommandDispatcher} for every command other than {@code AUTOWALK} itself, so any
     * manual input immediately overrides autopilot (issue #767).
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void cancelAutoWalkIfActive() {}

    /**
     * Handles the {@code CORPSE} command: reports where the player's tracked corpse lies, how much
     * gold it holds, how long remains before it decays, and turn-by-turn directions back to it — or
     * that they have no corpse in the world. With the {@code ALL} keyword it lists every outstanding
     * corpse; with a numeric index it reports that specific corpse in full.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the raw argument after {@code CORPSE} (blank, {@code ALL}, or a corpse number)
     */
    default void corpse(String args) {}

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
     * Sells every item in the player's inventory to the shop in the current room in one command
     * (the {@code SELL ALL} form), optionally narrowed to items whose name contains {@code keyword}.
     * Each item is paid at the shop's sell ratio and the player receives a single summarized line
     * with the item count and total gold earned. Equipped gear is never touched.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param keyword optional case-insensitive substring restricting which inventory items are sold;
     *                {@code null} sells the whole inventory
     */
    default void sellAllToShop(@Nullable String keyword) {}

    /**
     * Repairs the named damaged item in the player's possession, provided a blacksmith is present
     * in the current room. Charges gold proportional to the item's value and damage.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the item name to repair
     */
    default void repairItem(String args) {}

    /**
     * Repairs every breakable, damaged item the player is carrying in one call, provided a blacksmith
     * is present in the current room (the {@code REPAIR ALL} form). Items are repaired cheapest-first;
     * when the player cannot afford to fix everything, as many items as their gold allows are repaired
     * and the response lists the items still needing repair plus the extra gold required.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     */
    default void repairAllItems() {}

    /**
     * Executes the {@code CRAFT} command. With blank arguments it lists the recipes a blacksmith can
     * make, with live {@code have/need} material counts; with an item name it attempts to craft it,
     * consuming the required materials and gold. Requires a blacksmith in the current room.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the recipe or output item name to craft, or blank to list recipes
     */
    default void craft(String args) {}

    /**
     * Executes the {@code SALVAGE} command. With blank arguments it lists the carried, unequipped
     * weapon and armor items that can be broken down, previewing the material(s) each would yield;
     * with an item name it destroys that carried item and adds the yielded materials for its rarity
     * tier to the player's inventory. Requires a blacksmith in the current room.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the item name to salvage, or blank to list salvageable items
     */
    default void salvage(String args) {}

    /**
     * Executes the {@code BREW} command. With blank arguments it lists the potion recipes an alchemist
     * can make, with live {@code have/need} herb counts; with a potion name it attempts to brew it,
     * consuming the required herbs and gold. Requires an alchemist in the current room.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the recipe or output potion name to brew, or blank to list recipes
     */
    default void brew(String args) {}

    /**
     * Executes the {@code COOK} command. With blank arguments it lists the meal recipes a cook can
     * make, with live {@code have/need} ingredient counts; with a meal name it attempts to cook it,
     * consuming the required ingredients and gold. Requires a cook in the current room.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the recipe or output meal name to cook, or blank to list recipes
     */
    default void cook(String args) {}

    /**
     * Executes the {@code ENCHANT} command. With blank arguments it lists the enchantments an
     * Enchanter can apply, with each affix's stat bonus and live {@code have/need} material counts;
     * with an {@code <item> <enchantment>} argument it permanently imbues that carried, equippable
     * item with the named affix, consuming the required materials and gold. Requires an Enchanter in
     * the current room.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the {@code <item> <enchantment>} argument, or blank to list enchantments
     */
    default void enchant(String args) {}

    /**
     * Executes the {@code TAN} command. With blank arguments it lists the leather armor recipes a
     * leatherworker can make, with live {@code have/need} material counts; with an item name it
     * attempts to tan it, consuming the required pelts, fangs and gold. Requires a leatherworker in
     * the current room.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the recipe or output item name to tan, or blank to list recipes
     */
    default void tan(String args) {}

    /**
     * Executes the {@code CUT} command. With blank arguments it lists the ring and necklace recipes a
     * jeweler can make, with live {@code have/need} material counts; with an item name it attempts to
     * cut it, consuming the required raw gems and gold. Requires a jeweler in the current room.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the recipe or output item name to cut, or blank to list recipes
     */
    default void cut(String args) {}

    /**
     * Executes the {@code SEW} command. With blank arguments it lists the cloth armor recipes a tailor
     * can make, with live {@code have/need} material counts; with an item name it attempts to sew it,
     * consuming the required cloth and gold. Requires a tailor in the current room.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the recipe or output item name to sew, or blank to list recipes
     */
    default void sew(String args) {}

    /**
     * Executes the {@code GATHER} command, harvesting the raw-material yield of an available resource
     * node in the player's current room and adding it to their inventory. If no node is present, or
     * the node has already been harvested and not yet respawned, a clear failure message is sent and
     * nothing changes.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void gather() {}

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
     * Executes a DAILY_QUEST sub-command (empty/LIST, ACCEPT &lt;pool_id&gt;, STATUS, COMPLETE).
     *
     * <p>Lets players view the rotating daily quest active in each pool, accept it into their active
     * quest slot, track progress, and claim the daily bonus reward on completion.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "ACCEPT slayer"})
     */
    default void executeDailyQuest(String args) {}

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
     * Executes a TRADE sub-command (a player name to propose, ACCEPT, DECLINE, ADD, REMOVE, CONFIRM,
     * CANCEL, STATUS, or empty for status).
     *
     * <p>Manages a secure two-way item/gold exchange between two players in the same room. All
     * validation and the atomic swap run here on the tick thread; nothing leaves a player's
     * inventory until both parties confirm matching offers.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "ADD GOLD 100"})
     */
    default void executeTrade(String args) {}

    /**
     * Executes a MARRY sub-command: a player name to propose, {@code ACCEPT}, {@code DECLINE},
     * {@code DIVORCE}, {@code TELL <message>}, or empty/{@code STATUS} to report current state.
     *
     * <p>Manages the purely-opt-in, mechanically-inert marriage bond between two players. Proposals
     * are held in the transient marriage registry; the accepted bond is persisted on both
     * {@link Player} records. All validation and state changes run here on the tick thread.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "ACCEPT"} or {@code "Alice"})
     */
    default void executeMarry(String args) {}

    /**
     * Sends a private {@code SPOUSETELL} message to the caller's spouse (see the MARRY command),
     * wherever they are in the world. Delivery is exempt from {@code IGNORE}. Fails with a clear
     * message when the caller is unmarried, their spouse is offline, or the message is blank.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param message the message to send to the spouse
     */
    default void spouseTell(String message) {}

    /**
     * Executes a MENTOR sub-command: a player name to propose, {@code ACCEPT}, {@code DECLINE},
     * {@code END}, or empty/{@code STATUS} to report current state.
     *
     * <p>Manages the opt-in mentor bond (see issue #751) in which a veteran player boosts a partied
     * newcomer's XP. Proposals are held in the transient mentor registry; the accepted bond is
     * persisted on both {@link Player} records. All validation and state changes run here on the tick
     * thread.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "ACCEPT"} or {@code "Alice"})
     */
    default void executeMentor(String args) {}

    /**
     * Returns the {@code WHO}/roster marital-status suffix for the given online player, e.g.
     * {@code " (Married to Alice)"}, or an empty string when they are unmarried. Used by {@code WHO}
     * to annotate each name.
     *
     * <p>The default implementation returns an empty string so existing test stubs do not need to be
     * updated.
     *
     * @param username the player whose marital-status tag to resolve
     * @return the marriage suffix with a leading space, or {@code ""}
     */
    default String marriedTag(Username username) {
        return "";
    }

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
     * Sends a party-chat message to every currently-online member of the caller's party (the
     * {@code PTELL} verb, or free text after {@code PARTY}). The default implementation is a no-op so
     * existing test stubs do not need to be updated.
     *
     * @param message the message to broadcast to the party
     */
    default void partyChat(String message) {}

    /**
     * Executes a FOLLOW sub-command: reports the current leader when {@code args} is blank, stops
     * following on {@code OFF}, or starts auto-following the named online party member.
     *
     * <p>While following, the caller is moved one step behind the leader whenever the leader walks to
     * an adjacent room; the relationship is cancelled with a notice on combat, overburden, a blocked
     * exit, a party-membership change, or disconnect.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the target player name, {@code OFF}, or blank to report status
     */
    default void executeFollow(String args) {}

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

    /**
     * Attempts the rogue PICK skill on a locked container in the player's current room.
     *
     * <p>Only rogues of level 1 or higher may pick locks. Rolls an independent pick-success and trap
     * chance; a sprung trap damages the rogue, and a successful pick unlocks the container.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the container name or id to pick
     */
    default void pickLock(String args) {}

    /**
     * Toggles the rogue stealth (SNEAK/HIDE) state for the current player.
     *
     * <p>Only rogues may sneak. Activating stealth hides the rogue from fresh aggro by aggressive
     * mobs; using it again reveals them. The state also clears automatically when the rogue attacks
     * or uses an ability.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args unused command arguments
     */
    default void sneak(String args) {}

    /**
     * Searches the current room for undiscovered hidden exits (the SEARCH command).
     *
     * <p>Available to every class and level and repeatable freely. Each attempt has a chance to
     * reveal any secret exits in the room; on success the exit becomes visible and walkable for all
     * players. A miss (or a room with nothing left to find) prints a neutral "nothing new" line.
     * Searching is rejected in combat and breaks stealth like other deliberate actions.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void searchRoom() {}

    /**
     * Saddles the current player up on a rideable mount they own, reducing their per-step travel cost
     * while ridden.
     *
     * <p>The mount must be a carried mount item, the player must not already be mounted, and mounts
     * may only be summoned outdoors. The ridden state is transient (never persisted) and is broken
     * automatically on entering combat or moving indoors.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the name (or a fragment) of the mount to ride
     */
    default void mount(String args) {}

    /**
     * Dismounts the current player, returning them to normal per-step travel cost.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args unused command arguments
     */
    default void dismount(String args) {}

    /**
     * Attempts the rogue STEAL skill to pickpocket gold from a target NPC in the current room.
     *
     * <p>Only rogues may steal. A success roll scaling with rogue level either transfers gold from
     * the NPC to the thief or, on failure, is caught and turns the NPC hostile (aggressing the
     * thief).
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the NPC name to steal from
     */
    default void steal(String args) {}

    /**
     * Attempts the ranger TRACK skill, searching the world for the nearest mob of the named type and
     * reporting a directional hint toward it.
     *
     * <p>Only rangers may track. On success the player is told the compass direction of the first
     * step toward the nearest matching mob (or that it shares their room); on failure an error
     * explains why (not a ranger, no such mob anywhere, etc.).
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the mob type/name to track (e.g. {@code "goblin"})
     */
    default void track(String args) {}

    /**
     * Casts the necromancer-style SUMMON spell, spawning a temporary pet mob that fights hostile
     * mobs at the caster's side, or dismisses the active pet when {@code args} is {@code "dismiss"}.
     *
     * <p>Requires the player to have learned the summon spell and to meet its level and mana cost;
     * the spell honours its own cooldown. Only one pet may be active at a time.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the sub-command: blank to summon, or {@code "dismiss"} to release the active pet
     */
    default void summon(String args) {}

    /**
     * Permanently tames a charmable mob in the player's room, turning it into a persistent companion
     * that follows its owner between rooms and fights at their side.
     *
     * <p>Only mobs flagged {@code charmable} can be tamed, and a player may control at most a fixed
     * number of companions at once. Tamed companions are saved to the player's file so they survive
     * logout/login. The game logic lives in {@code MobRegistry.processTame}.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the mob name to tame
     */
    default void tame(String args) {}

    /**
     * Lists the player's active tamed companions, with each pet's location and current HP.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void companions() {}

    /**
     * Gives one of the player's own tamed companions a custom display name (see the NAME command),
     * which then replaces the template name everywhere the companion is shown — {@code COMPANIONS},
     * room {@code LOOK}, and combat/room broadcasts — for the owner and other players.
     *
     * <p>The companion is matched by its current display or template name (prefix match, first
     * occurrence); the name is validated (non-blank, capped length) and persisted so it survives
     * logout/login and respawn. The game logic lives in {@code MobRegistry.nameCompanion}.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the raw arguments: {@code <companion> <new name>}
     */
    default void nameCompanion(String args) {}

    /**
     * Deposits the specified amount of gold from the player's carried balance into the bank.
     *
     * <p>Requires the player to be in the same room as a bank NPC.
     * Fails with a clear message if no bank is present, the amount is invalid,
     * or the player does not carry enough gold.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the raw amount string (e.g. {@code "100"})
     */
    default void depositToBank(String args) {}

    /**
     * Withdraws the specified amount of gold from the bank into the player's carried balance.
     *
     * <p>Requires the player to be in the same room as a bank NPC.
     * Fails with a clear message if no bank is present, the amount is invalid,
     * or the bank does not hold enough gold for this player.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the raw amount string (e.g. {@code "50"})
     */
    default void withdrawFromBank(String args) {}

    /**
     * Stores an item from the player's carried inventory into their personal bank vault.
     *
     * <p>Requires the player to be in the same room as a bank NPC. Unequips the item first if worn.
     * Fails with a clear message if no bank is present, the item isn't found, or the vault is full.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need updating.
     *
     * @param args the item name to store
     */
    default void storeItemInBank(String args) {}

    /**
     * Claims an item from the player's bank vault back into their carried inventory.
     *
     * <p>Requires the player to be in the same room as a bank NPC. Fails with a clear message if no
     * bank is present, the item isn't in the vault, or claiming it would exceed the carry weight.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need updating.
     *
     * @param args the item name to claim
     */
    default void claimItemFromBank(String args) {}

    /**
     * Sends the current player's bank-vault listing (stored items), showing slots used versus their
     * effective capacity and, when not yet at the top tier, the cost and slot gain of the next
     * {@code VAULT UPGRADE}.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need updating.
     */
    default void sendVault() {}

    /**
     * Buys the next vault-capacity upgrade tier for the current player, permanently raising their
     * personal vault size in exchange for carried gold.
     *
     * <p>Requires the player to be in the same room as a bank NPC. Fails with a clear message if no
     * bank is present, the player cannot afford the next tier, or the vault is already at the top
     * tier.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need updating.
     */
    default void upgradeVault() {}

    /**
     * Executes an ALIAS sub-command: lists the player's aliases when {@code args} is
     * blank, removes an alias with {@code -d <name>}, or defines/overwrites an alias
     * with {@code <name> <expansion>}.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "k kill"} or {@code "-d k"})
     */
    default void manageAlias(String args) {}

    /**
     * Executes an IGNORE sub-command: lists the ignored players when {@code args} is blank
     * (or {@code LIST}), adds a player with {@code ADD <name>}, removes one with
     * {@code REMOVE <name>}, or clears the whole list with {@code CLEAR}.
     *
     * <p>Ignoring silently mutes TELL/SAY from the named player; the sender is never told they
     * have been ignored. The relationship is one-directional and persisted per player.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "ADD Alice"} or {@code "CLEAR"})
     */
    default void manageIgnore(String args) {}

    /**
     * Executes a FRIEND sub-command: lists the player's friends and their online status when
     * {@code args} is blank (or {@code LIST}), adds a player with {@code ADD <name>}, removes one
     * with {@code REMOVE <name>}, or clears the whole list with {@code CLEAR}.
     *
     * <p>The friend relationship is one-directional and requires no consent. Friends are persisted
     * per player, highlighted in {@code WHO}, and the friending player is notified when a friend
     * enters or leaves the game.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be
     * updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "ADD Alice"} or {@code "CLEAR"})
     */
    default void manageFriends(String args) {}

    /**
     * Returns {@code true} when the given player is on the current (viewing) player's friends list.
     * Used by {@code WHO} to visually distinguish friends from other online players.
     *
     * <p>The default implementation returns {@code false} so existing test stubs do not need to be
     * updated.
     *
     * @param username the player to test against the viewer's friends list
     * @return {@code true} when {@code username} is a friend of the viewing player
     */
    default boolean isFriend(Username username) {
        return false;
    }

    /**
     * Notifies every other currently-online player who has {@code player} on their {@code FRIEND}
     * list that {@code player} has just entered the game, delivering
     * {@code "Your friend <Name> has entered the game."} to each of them.
     *
     * <p>The relationship is one-directional (only players who friended {@code player} are notified),
     * the player is never notified about themselves, and offline friends receive nothing. Fired once
     * on a genuine login, never on a linkdead reattach.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param player the player who has just logged in
     */
    default void notifyFriendsOfLogin(Player player) {}

    /**
     * Notifies every other currently-online player who has {@code player} on their {@code FRIEND}
     * list that {@code player} has just left the game, delivering
     * {@code "Your friend <Name> has left the game."} to each of them.
     *
     * <p>The relationship is one-directional (only players who friended {@code player} are notified),
     * the player is never notified about themselves, and offline friends receive nothing. Fired once
     * on a genuine logout (quit, disconnect, or linkdead-timeout expiry), never on a linkdead
     * reattach.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param player the player who has just left the game
     */
    default void notifyFriendsOfLogout(Player player) {}

    /**
     * Executes a MAIL sub-command: lists the player's mail when {@code args} is blank,
     * shows a full message with {@code READ <n>}, removes a message with {@code DELETE <n>},
     * or leaves a message for a named player (who may be offline) with
     * {@code <playername> <message>}.
     *
     * <p>The default implementation is a no-op so that existing test stubs
     * do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "READ 2"} or
     *             {@code "Alice hello there"})
     */
    default void manageMail(String args) {}

    /**
     * Executes an AUCTION sub-command at the Auction House: {@code LIST} shows all active listings,
     * {@code SELL <item> <price>} lists an item from the player's inventory, {@code BUY <#>} purchases
     * a listing, and {@code CANCEL <#>} withdraws the player's own listing.
     *
     * <p>Every form requires the player to be standing in the Auction House room. A completed sale
     * credits the (possibly offline) seller's gold and mails them a notification via the same
     * cross-player update path as {@code MAIL}.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "SELL longsword 100"} or
     *             {@code "BUY 2"})
     */
    default void manageAuction(String args) {}

    /**
     * Executes a {@code BOUNTY} sub-command: {@code POST <mob> <gold>}, {@code LIST}, or
     * {@code CANCEL <mob>}. Escrows gold against a mob type, lists open bounties, or refunds the
     * player's own unclaimed stake respectively; a bountied mob type's pooled reward is paid to
     * whoever next kills it (announced server-wide) via the mob-death reward path.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "POST goblin 100"} or
     *             {@code "CANCEL goblin"})
     */
    default void manageBounty(String args) {}

    /**
     * Displays the bulletin board of the player's current room via the {@code BOARD} command:
     * every note pinned there, numbered oldest-first, with each note's author, timestamp, and text.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void showBoard() {}

    /**
     * Executes a {@code NOTE} sub-command against the current room's bulletin board:
     * {@code POST <message>} pins a new note, and {@code DELETE <number>} removes one of the
     * player's own notes by its board number (as shown by {@code BOARD}).
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "POST hello all"} or
     *             {@code "DELETE 2"})
     */
    default void manageNote(String args) {}

    /**
     * Initiates a conversation with a named NPC in the current room via the {@code TALK} command.
     *
     * <p>Finds a living mob whose name matches {@code npcName} and which offers a dialogue tree,
     * begins the conversation at the tree's start node, and displays the NPC's opening line and
     * numbered response options. Fails with a clear message when no matching NPC is present or the
     * NPC has nothing to say.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param npcName the NPC name to talk to
     */
    default void talk(String npcName) {}

    /**
     * Selects a numbered dialogue response via the {@code RESPOND} command, advancing the active
     * conversation and displaying the NPC's next line and options.
     *
     * <p>Fails with a clear message when the player is not currently in a conversation, has left the
     * NPC's room, or supplies an invalid response number. Reaching a terminal node ends the
     * conversation.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param numberInput the raw 1-based response number entered by the player
     */
    default void respond(String numberInput) {}

    /**
     * Displays the player's milestone achievements via the {@code ACHIEVEMENTS} command: unlocked
     * achievements with the date/time they were earned, and locked achievements with current
     * progress toward their threshold (e.g. {@code 5/100 kills}).
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void showAchievements() {}

    /**
     * Challenges a player in the same room to a consensual duel via the {@code DUEL} command.
     *
     * <p>Sends a private challenge to the named target, who may respond with {@code ACCEPT} within the
     * timeout window. Fails with a clear message when the name is blank, the target is absent, either
     * party is already in combat, or the player targets themselves.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param targetName the name of the player to challenge
     */
    default void initiateDuel(String targetName) {}

    /**
     * Challenges a player in the same room to a consensual duel staked with a gold wager via the
     * {@code DUEL WAGER <player> <gold>} command (issue #661).
     *
     * <p>{@code args} is the text following {@code WAGER}, expected to be a player name followed by a
     * positive whole-number gold amount. Fails with a clear message when the amount is missing or not
     * a positive integer, and otherwise defers to the ordinary duel validation (including a check that
     * the challenger currently holds the staked gold). No gold is escrowed at challenge time.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     *
     * @param args the {@code <player> <gold>} text following {@code WAGER}
     */
    default void initiateWagerDuel(String args) {}

    /**
     * Accepts a pending duel challenge via the {@code ACCEPT} command, engaging both participants.
     *
     * <p>Fails with a clear message when the player has no pending challenge or is already dueling.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void acceptDuel() {}

    /**
     * Executes a GUILD sub-command (CREATE, INVITE, ACCEPT, DECLINE, LEAVE, KICK, DISBAND, WHO, BANK,
     * DEPOSIT, WITHDRAW, VAULT, STORE, CLAIM), or, when the first token is not a recognised
     * sub-command, treats the whole argument string as a guild-chat message to the caller's guild.
     *
     * <p>Manages persistent player guilds: founding, membership, leadership transfer, disbanding, and
     * the guild-only chat channel. The default implementation is a no-op so existing test stubs do not
     * need to be updated.
     *
     * @param args the sub-command and optional arguments (e.g. {@code "CREATE Ironclad"} or a chat line)
     */
    default void executeGuild(String args) {}

    /**
     * Sends a guild-chat message to every currently-online member of the caller's guild (the {@code GC}
     * alias). The default implementation is a no-op so existing test stubs do not need to be updated.
     *
     * @param message the message to broadcast to the guild
     */
    default void guildChat(String message) {}

    /**
     * Shows the caller's guild its active cooperative guild quest: the objective, shared progress
     * ({@code Slayed 7 / 20 dire wolves}) and the gold reward paid into the treasury on completion.
     * Reports "You are not in a guild." when the caller is guildless. The default implementation is a
     * no-op so existing test stubs do not need to be updated.
     *
     * @param args unused sub-arguments (the command takes none)
     */
    default void executeGuildQuest(String args) {}

    /**
     * Returns the guild tag suffix (e.g. {@code " [Ironclad]"}) for the given online player, or an
     * empty string when they belong to no guild. Used by {@code WHO} to annotate each name.
     *
     * <p>The default implementation returns an empty string so existing test stubs do not need to be
     * updated.
     *
     * @param username the player whose guild tag to resolve
     * @return the bracketed tag with a leading space, or {@code ""}
     */
    default String guildTag(Username username) {
        return "";
    }

    /**
     * Returns the active-title suffix (e.g. {@code " the Centurion"}) for the given online player,
     * or an empty string when they have not selected a title. Used by {@code WHO} to annotate each
     * name after the guild tag.
     *
     * <p>The default implementation returns an empty string so existing test stubs do not need to be
     * updated.
     *
     * @param username the player whose active title to resolve
     * @return the title suffix with a leading {@code " the "}, or {@code ""}
     */
    default String activeTitle(Username username) {
        return "";
    }

    /**
     * Handles the {@code TITLE} command family: listing earned titles, selecting an earned title as
     * active, or clearing the active title.
     *
     * <p>The default implementation is a no-op so existing test stubs do not need to be updated.
     *
     * @param args the raw argument string following {@code TITLE} (may be blank, a title name,
     *             {@code NONE}, or {@code CLEAR})
     */
    default void manageTitle(String args) {}

    /**
     * Handles the {@code DESCRIBE} command family: showing the caller's custom LOOK description,
     * setting it (capped at 240 characters), or clearing it back to the default generated line.
     *
     * <p>The default implementation is a no-op so existing test stubs do not need to be updated.
     *
     * @param args the raw argument string following {@code DESCRIBE} (may be blank, free text,
     *             {@code CLEAR}, or {@code NONE})
     */
    default void manageDescription(String args) {}

    /**
     * Handles the {@code AFK} command: with no argument it toggles the caller's away status on (with
     * a default message) or off; with an argument it turns away status on with that custom reason.
     *
     * <p>The status is transient session state (issue #464), never persisted, and is cleared
     * automatically by the player's next command via {@link #clearAwayIfActive()}.
     *
     * <p>The default implementation is a no-op so existing test stubs do not need to be updated.
     *
     * @param args the optional custom away message, or blank to toggle
     */
    default void toggleAfk(String args) {}

    /**
     * Clears the caller's away status if currently AFK, printing a short "welcome back" confirmation.
     * Invoked by {@link SocketCommandDispatcher} for every command other than {@code AFK} itself, so
     * a player never has to remember to clear their own AFK marker.
     *
     * <p>The default implementation is a no-op so existing test stubs do not need to be updated.
     */
    default void clearAwayIfActive() {}

    /**
     * Returns whether the given online player is currently marked away from keyboard. Used by
     * {@code WHO} to render an {@code [AFK]} tag next to away players.
     *
     * <p>The default implementation returns {@code false} so existing test stubs do not need to be
     * updated.
     *
     * @param username the player to test
     * @return {@code true} when that player's session is currently AFK
     */
    default boolean isPlayerAway(Username username) {
        return false;
    }

    /**
     * Returns the AFK notice line to show a message sender when the given recipient is away, or empty
     * when the recipient is not away. The notice reads {@code "<Name> is AFK: <reason>"} when a custom
     * reason was set, or {@code "<Name> is AFK."} otherwise. Used by {@code TELL}/{@code WHISPER}/
     * {@code REPLY} to notify the sender (only) that their message reached an away player.
     *
     * <p>The default implementation returns empty so existing test stubs do not need to be updated.
     *
     * @param username the message recipient to check
     * @return the sender-facing AFK notice, or empty when the recipient is not away
     */
    default java.util.Optional<String> awayNotice(Username username) {
        return java.util.Optional.empty();
    }

    /**
     * Handles the {@code LFG} command (issue #510): with no argument it toggles the caller's
     * looking-for-group status on (with a default marker) or off; with an argument it turns the
     * status on with that custom note; with {@code STATUS} it reports the current state without
     * toggling.
     *
     * <p>The status is transient session state, never persisted, and — unlike {@code AFK} — is
     * <em>not</em> cleared by the player's next command; it stays on until toggled off or the player
     * disconnects.
     *
     * <p>The default implementation is a no-op so existing test stubs do not need to be updated.
     *
     * @param args the optional custom LFG note, {@code STATUS}, or blank to toggle
     */
    default void toggleLfg(String args) {}

    /**
     * Returns whether the given online player is currently flagged looking-for-group. Used by
     * {@code WHO} to render an {@code [LFG]} tag next to those players.
     *
     * <p>The default implementation returns {@code false} so existing test stubs do not need to be
     * updated.
     *
     * @param username the player to test
     * @return {@code true} when that player's session is currently LFG
     */
    default boolean isPlayerLfg(Username username) {
        return false;
    }

    /**
     * Returns the {@code WHO}-roster suffix for the given online player's looking-for-group status,
     * e.g. {@code " [LFG]"} or {@code " [LFG: tank for Catacombs]"}, or an empty string when they are
     * not LFG. Used by {@code WHO} to annotate each name after the other markers.
     *
     * <p>The default implementation returns an empty string so existing test stubs do not need to be
     * updated.
     *
     * @param username the player whose LFG tag to resolve
     * @return the LFG suffix with a leading space, or {@code ""}
     */
    default String lfgTag(Username username) {
        return "";
    }

    /**
     * Returns the {@code WHO}-roster level/class suffix for the given online player, e.g.
     * {@code " [12 Warrior]"}, resolving the class display name from the class registry. Returns an
     * empty string when the player is not online or their class cannot be resolved.
     *
     * <p>The default implementation returns an empty string so existing test stubs do not need to be
     * updated.
     *
     * @param username the player whose level/class tag to resolve
     * @return the level/class suffix with a leading space, or {@code ""}
     */
    default String levelClassTag(Username username) {
        return "";
    }

}
