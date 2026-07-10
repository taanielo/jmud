package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code TRADE} command, a secure two-way item and gold exchange between two players in
 * the same room.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code TRADE <player>}           — propose a trade to a player in your room</li>
 *   <li>{@code TRADE ACCEPT}             — accept a pending proposal</li>
 *   <li>{@code TRADE DECLINE}            — decline a pending proposal</li>
 *   <li>{@code TRADE ADD <item>}         — add an inventory item to your offer</li>
 *   <li>{@code TRADE ADD GOLD <amount>}  — add gold to your offer</li>
 *   <li>{@code TRADE REMOVE <item>}      — take an item back out of your offer</li>
 *   <li>{@code TRADE CONFIRM}            — lock in your offer; the swap runs once both confirm</li>
 *   <li>{@code TRADE CANCEL}             — cancel the trade at any point before completion</li>
 *   <li>{@code TRADE} / {@code TRADE STATUS} — show the current offer status</li>
 * </ul>
 *
 * <p>This command performs parsing only; all validation and the atomic swap run on the tick thread
 * via {@link SocketCommandContext#executeTrade(String)} (AGENTS.md §5).
 */
public class TradeCommand extends RegistrableCommand {

    /**
     * Creates a {@code TradeCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public TradeCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "trade";
    }

    @Override
    public String shortDescription() {
        return "Securely swap items and gold with a player in your room.";
    }

    @Override
    public String longDescription() {
        return "Usage: TRADE [sub-command] [args]\n"
             + "  TRADE <player>          — propose a trade to a player in your room\n"
             + "  TRADE ACCEPT            — accept a pending trade proposal\n"
             + "  TRADE DECLINE           — decline a pending trade proposal\n"
             + "  TRADE ADD <item>        — add an inventory item to your offer\n"
             + "  TRADE ADD GOLD <amount> — add gold to your offer\n"
             + "  TRADE REMOVE <item>     — take an item back out of your offer\n"
             + "  TRADE CONFIRM           — lock in your offer (swap runs once both confirm)\n"
             + "  TRADE CANCEL            — cancel the trade before it completes\n"
             + "  TRADE STATUS            — show the current offer status\n"
             + "\n"
             + "Nothing leaves your inventory until both parties confirm matching offers; any change\n"
             + "to either offer after a confirm clears both confirmations.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"TRADE".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeTrade(args)));
    }
}
