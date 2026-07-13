package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code NAME <companion> <new name>} command, which gives one of the player's own tamed
 * companions a custom display name.
 *
 * <p>Once set, the custom name replaces the companion's template name everywhere its identity is
 * shown — the {@code COMPANIONS} listing, room {@code LOOK}, and combat/room broadcast messages — for
 * both the owner and other players in the room. Names are free text capped at a reasonable length and
 * persist across logout/login and respawn. The game logic lives in {@code MobRegistry.nameCompanion}
 * via {@link SocketCommandContext#nameCompanion(String)}.
 */
public class NameCommand extends RegistrableCommand {

    public NameCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "name";
    }

    @Override
    public String shortDescription() {
        return "Give one of your tamed companions a custom name.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: NAME <companion> <new name>
                 Gives one of your tamed companions a custom name (e.g. NAME WOLF Fluffy). The name
                 replaces the creature's kind everywhere it is shown — COMPANIONS, LOOK, and combat
                 messages — and is saved so it survives logout. Match the companion by its kind or
                 its current name; renaming an already-named companion overwrites the old name. Names
                 are capped at 24 characters. Use COMPANIONS to list your tamed pets.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"NAME".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.isBlank()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: NAME <companion> <new name>")));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.nameCompanion(args)));
    }
}
