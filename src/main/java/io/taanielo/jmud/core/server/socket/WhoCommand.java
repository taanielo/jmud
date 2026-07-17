package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.weather.WeatherEngine;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Handles the {@code who} command, listing all connected and authenticated
 * players together with a total online count.
 *
 * <p>The command is read-only: it inspects live session state via the context
 * and never reads or writes persisted data. Formatting is delegated to
 * {@link WhoListing} so the listing logic stays testable without networking.
 */
public class WhoCommand extends RegistrableCommand {

    private final @Nullable RoomService roomService;
    private final @Nullable WeatherEngine weatherEngine;

    /**
     * Creates a WhoCommand with no weather visibility line.
     *
     * @param registry the command registry to register with
     */
    public WhoCommand(SocketCommandRegistry registry) {
        this(registry, null, null);
    }

    /**
     * Creates a WhoCommand that also shows the viewer's local weather visibility line.
     *
     * @param registry      the command registry to register with
     * @param roomService   service used to resolve the viewer's current room; may be null
     * @param weatherEngine weather source used for the visibility line; may be null
     */
    public WhoCommand(
            SocketCommandRegistry registry,
            @Nullable RoomService roomService,
            @Nullable WeatherEngine weatherEngine) {
        super(registry);
        this.roomService = roomService;
        this.weatherEngine = weatherEngine;
    }

    @Override
    public String name() {
        return "who";
    }

    @Override
    public String shortDescription() {
        return "List all players currently online.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: WHO
                 Displays a list of every authenticated player connected to the server.
                 Each entry shows the player's level and class, e.g. Sparky [12 Warrior], plus any
                 guild tag, title, [AFK] marker, and [LFG] looking-for-group tag (see HELP LFG).\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"WHO".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, this::handleWho));
    }

    private void handleWho(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to see who is online.");
            return;
        }
        List<Username> onlineNames = context.onlinePlayerNames();
        for (String line : WhoListing.format(
                onlineNames, context::guildTag, context::activeTitle, context::isFriend,
                context::isPlayerAway, context::levelClassTag, context::lfgTag, context::marriedTag)) {
            context.writeLineSafe(line);
        }
        Player player = context.getPlayer();
        if (roomService != null && player != null) {
            WeatherVisibilityLine.forPlayer(weatherEngine, roomService, player)
                .ifPresent(line -> context.writeLineSafe("Weather: " + line));
        }
        context.sendPrompt();
    }
}
