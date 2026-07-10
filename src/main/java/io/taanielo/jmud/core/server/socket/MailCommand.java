package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code MAIL} command, giving every player a persistent, offline mailbox.
 *
 * <p>Six forms are supported:
 * <ul>
 *   <li>{@code MAIL}                          — lists mail waiting for the player.</li>
 *   <li>{@code MAIL READ <n>}                 — shows the full text of message {@code n}.</li>
 *   <li>{@code MAIL DELETE <n>}                — removes message {@code n} from the mailbox.</li>
 *   <li>{@code MAIL <playername> <message>}   — leaves a message for the named player, who
 *       need not be online.</li>
 *   <li>{@code MAIL GOLD <playername> <amount> <message>} — leaves a message with a gold
 *       attachment; the gold is deducted from the sender immediately and credited to the
 *       recipient when they read or delete the message.</li>
 *   <li>{@code MAIL ITEM <playername> <itemname> <message>} — leaves a message with a single item
 *       attachment; the item leaves the sender's inventory immediately (unequipping it first if
 *       worn) and is credited to the recipient when they read or delete the message.</li>
 * </ul>
 *
 * <p>Unlike {@link TellCommand}, the target does not need to be connected: delivery is
 * persisted via the normal player-repository/persistence-queue path so it survives across
 * sessions and server restarts. Logic lives in
 * {@link SocketCommandContext#manageMail} / {@code PlayerMailService} so it stays
 * unit-testable without sockets (AGENTS.md §10).
 */
public class MailCommand extends RegistrableCommand {

    /**
     * Creates a {@code MailCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public MailCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "mail";
    }

    @Override
    public String shortDescription() {
        return "Leave an offline message for a player, or read/list/delete your own mail.";
    }

    @Override
    public String longDescription() {
        return "Usage: MAIL  |  MAIL READ <n>  |  MAIL DELETE <n>  |  MAIL <playername> <message>"
             + "  |  MAIL GOLD <playername> <amount> <message>"
             + "  |  MAIL ITEM <playername> <itemname> <message>\n"
             + "  MAIL                        — list mail waiting for you.\n"
             + "  MAIL READ <n>                — show the full text of message n.\n"
             + "  MAIL DELETE <n>              — delete message n.\n"
             + "  MAIL <playername> <message>  — leave a message for a player (need not be online).\n"
             + "  MAIL GOLD <playername> <amount> <message> — attach gold to a message.\n"
             + "  MAIL ITEM <playername> <itemname> <message> — attach an item from your inventory "
             + "to a message.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"MAIL".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageMail(args)));
    }
}
