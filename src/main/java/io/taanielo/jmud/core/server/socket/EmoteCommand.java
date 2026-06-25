package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.player.Player;

/**
 * Handles the {@code EMOTE} / {@code ME} command, which lets a player express
 * a free-form action from the third-person perspective.
 *
 * <p>Usage: {@code EMOTE <text>}  or  {@code ME <text>}
 *
 * <p>The player's name is automatically prepended to the action text:
 * <ul>
 *   <li>The acting player sees: {@code You emote: <Name> <text>}</li>
 *   <li>Every other player in the same room sees: {@code <Name> <text>}</li>
 *   <li>Players in other rooms see nothing.</li>
 * </ul>
 *
 * <p>Invoking the command without any text prints: {@code Emote what?}
 */
public class EmoteCommand extends RegistrableCommand {

    /**
     * Creates an {@code EmoteCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public EmoteCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "emote";
    }

    @Override
    public String shortDescription() {
        return "Express a free-form action visible to everyone in the room. Aliases: ME";
    }

    @Override
    public String longDescription() {
        return "Usage: EMOTE <text>  |  ME <text>\n"
             + "  Describes an action from the third-person perspective.\n"
             + "  Your name is prepended automatically.\n"
             + "  You see: You emote: <YourName> <text>\n"
             + "  Others in the room see: <YourName> <text>\n"
             + "  Aliases: ME";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"EMOTE".equals(token) && !"ME".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleEmote(context, args)));
    }

    private void handleEmote(SocketCommandContext context, String text) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to emote.");
            return;
        }
        if (text == null || text.isBlank()) {
            context.writeLineWithPrompt("Emote what?");
            return;
        }
        Player player = context.getPlayer();
        String playerName = player.getUsername().getValue();
        String action = playerName + " " + text.trim();
        context.sendToRoom(player, action);
        context.writeLineSafe("You emote: " + action);
        context.sendPrompt();
    }
}
