package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the CONSIDER command, letting players assess how dangerous a mob is
 * before initiating combat.
 *
 * <p>Aliases: {@code CON}.
 *
 * <p>The command performs a prefix-match against live mobs in the player's
 * current room and prints a single qualitative danger line based on the ratio
 * of the mob's {@code maxHp} to the player's {@code maxHp}.
 */
public class ConsiderCommand extends RegistrableCommand {

    public ConsiderCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "consider";
    }

    @Override
    public String shortDescription() {
        return "Assess a mob's danger before attacking. Aliases: CON";
    }

    @Override
    public String longDescription() {
        return "Usage: CONSIDER <target>  |  CON <target>\n"
             + "  Compares the target mob's power against your own and prints a qualitative\n"
             + "  danger assessment. Prefix matching is supported (e.g. CON gob matches Goblin).";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if ("CONSIDER".equals(token) || "CON".equals(token)) {
            String args = parts[1];
            return Optional.of(new SocketCommandMatch(this, context -> context.considerMob(args)));
        }
        return Optional.empty();
    }
}
