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

}
