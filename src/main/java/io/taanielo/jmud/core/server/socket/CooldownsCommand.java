package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code COOLDOWNS} / {@code CD} command, which lists every ability the
 * player has learned along with its type and live readiness (Ready, or the number of
 * ticks remaining until it comes off cooldown).
 *
 * <p>Unlike {@code ABILITIES}, which prints each ability's static base cooldown length,
 * this command reads the player's own {@code CooldownSystem} so the status reflects the
 * ability's actual current readiness.
 *
 * <p>Usage: {@code COOLDOWNS} or {@code CD}
 */
public class CooldownsCommand extends RegistrableCommand {

    /**
     * Creates a {@code CooldownsCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public CooldownsCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "cooldowns";
    }

    @Override
    public String shortDescription() {
        return "List your learned abilities with live cooldown status. Aliases: CD";
    }

    @Override
    public String longDescription() {
        return """
               Usage: COOLDOWNS
                 Displays a formatted table of every ability you have learned.
                 Each row shows: ability name, type (SKILL/SPELL), and live status
                 (Ready, or the number of ticks remaining until it is ready again).
                 Alias: CD\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"COOLDOWNS".equals(token) && !"CD".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendCooldowns));
    }
}
