package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code AUCTION} command, the entry point to the player-to-player Auction House.
 *
 * <p>Every form requires the player to be standing in the Auction House room (the presence check is
 * enforced in {@link SocketCommandContext#manageAuction}, mirroring how {@code DEPOSIT}/{@code
 * WITHDRAW} require a bank room). Supported forms:
 * <ul>
 *   <li>{@code AUCTION LIST [keyword|MINE]} — list active listings (all, name-filtered, or your own),
 *       cheapest first.</li>
 *   <li>{@code AUCTION SELL <item> <price>} — list an item from your inventory for a gold price.</li>
 *   <li>{@code AUCTION BUY <#>}            — buy the numbered listing.</li>
 *   <li>{@code AUCTION CANCEL <#>}         — cancel your own numbered listing.</li>
 * </ul>
 *
 * <p>Gold reaches the seller even while they are offline (the same offline-delivery path as
 * {@code MAIL}); listings expire after a configurable number of ticks and are returned to their
 * sellers. Command logic lives in {@link SocketCommandContext#manageAuction} / {@code AuctionService}
 * so it stays unit-testable without sockets (AGENTS.md §10).
 */
public class AuctionCommand extends RegistrableCommand {

    /**
     * Creates an {@code AuctionCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public AuctionCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "auction";
    }

    @Override
    public String shortDescription() {
        return "Buy and sell items via the Auction House. Aliases: AH";
    }

    @Override
    public String longDescription() {
        return """
               Usage: AUCTION LIST [keyword|MINE] | AUCTION SELL <item> <price> | AUCTION BUY <#> | AUCTION CANCEL <#>
                 AUCTION LIST                  \u2014 list all active listings (item, price, seller, ticks left),
                                                 cheapest first.
                 AUCTION LIST <keyword>        \u2014 list only listings whose item name contains the keyword.
                 AUCTION LIST MINE             \u2014 list only your own active listings.
                 AUCTION SELL <item> <price>   \u2014 list an item from your inventory for a gold price.
                 AUCTION BUY <#>               \u2014 buy the listing with the given number.
                 AUCTION CANCEL <#>            \u2014 cancel your own listing and get the item back.
                 Displayed numbers are stable across filters, so BUY/CANCEL always target the same listing.
                 Must be used at the Auction House. Sold gold reaches the seller even while offline;
                 listings expire after a while and are returned to the seller.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"AUCTION".equals(parts[0]) && !"AH".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageAuction(args)));
    }
}
