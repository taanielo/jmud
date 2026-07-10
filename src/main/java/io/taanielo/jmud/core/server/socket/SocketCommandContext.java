package io.taanielo.jmud.core.server.socket;

import java.util.List;

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
     * Renders and sends an ASCII minimap of the rooms the player has explored around their current
     * location (the {@code MAP} command).
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void sendMap() {}

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
     * Sends the current player's bank-vault listing (stored items).
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need updating.
     */
    default void sendVault() {}

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
     * Accepts a pending duel challenge via the {@code ACCEPT} command, engaging both participants.
     *
     * <p>Fails with a clear message when the player has no pending challenge or is already dueling.
     *
     * <p>The default implementation is a no-op so that existing test stubs do not need to be updated.
     */
    default void acceptDuel() {}

    /**
     * Executes a GUILD sub-command (CREATE, INVITE, ACCEPT, DECLINE, LEAVE, KICK, DISBAND, WHO), or,
     * when the first token is not a recognised sub-command, treats the whole argument string as a
     * guild-chat message to the caller's guild.
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

}
