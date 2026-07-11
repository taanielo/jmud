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
        return """
               Usage: TRADE [sub-command] [args]
                 TRADE <player>          \u2014 propose a trade to a player in your room
                 TRADE ACCEPT            \u2014 accept a pending trade proposal
                 TRADE DECLINE           \u2014 decline a pending trade proposal
                 TRADE ADD <item>        \u2014 add an inventory item to your offer
                 TRADE ADD GOLD <amount> \u2014 add gold to your offer
                 TRADE REMOVE <item>     \u2014 take an item back out of your offer
                 TRADE CONFIRM           \u2014 lock in your offer (swap runs once both confirm)
                 TRADE CANCEL            \u2014 cancel the trade before it completes
                 TRADE STATUS            \u2014 show the current offer status

               Nothing leaves your inventory until both parties confirm matching offers; any change
               to either offer after a confirm clears both confirmations.\
               """;
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
