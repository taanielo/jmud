package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code COMPANIONS} (alias {@code PETS}) command, which lists the player's active tamed
 * companions along with each pet's location and current HP.
 *
 * <p>The listing is produced by {@code MobRegistry.listCompanions} via
 * {@link SocketCommandContext#companions()}.
 */
public class CompanionsCommand extends RegistrableCommand {

    public CompanionsCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "companions";
    }

    @Override
    public String shortDescription() {
        return "List your active tamed companions (alias: PETS).";
    }

    @Override
    public String longDescription() {
        return "Usage: COMPANIONS | PETS\n"
             + "  Lists the mobs you have tamed as companions, with each pet's current location and\n"
             + "  hit points. Tame new companions with the TAME command.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.splitInput(input)[0];
        if (!"COMPANIONS".equals(token) && !"PETS".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::companions));
    }
}
