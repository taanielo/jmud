package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code BOUNTY} command, the entry point to player-funded mob bounties.
 *
 * <p>Any player may stake gold against a mob <em>type</em>, payable to whoever next kills a mob of
 * that type anywhere in the world. Supported forms:
 * <ul>
 *   <li>{@code BOUNTY POST <mob> <gold>} — escrow gold against a mob type; a second poster on the
 *       same type stacks as another backer, pooling the reward.</li>
 *   <li>{@code BOUNTY LIST}             — every open bounty server-wide: mob, total reward, backers,
 *       and age.</li>
 *   <li>{@code BOUNTY CANCEL <mob>}     — pull your own unclaimed stake and get a full refund.</li>
 * </ul>
 *
 * <p>When any player kills a bountied mob type the pooled reward pays out to the killer (split across
 * their eligible party like a mob's gold drop), a server-wide announcement names the killer, mob, and
 * total, and the paid bounties close. Command logic lives in
 * {@link SocketCommandContext#manageBounty} / {@code BountyService} so it stays unit-testable without
 * sockets (AGENTS.md §10).
 */
public class BountyCommand extends RegistrableCommand {

    /**
     * Creates a {@code BountyCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public BountyCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "bounty";
    }

    @Override
    public String shortDescription() {
        return "Put gold behind a mob type, payable to whoever kills it next.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: BOUNTY POST <mob> <gold> | BOUNTY LIST | BOUNTY CANCEL <mob>
                 BOUNTY POST <mob> <gold>  — escrow gold against a mob type; the gold is taken from you
                                             now and paid to whoever next kills that mob type anywhere.
                                             Posting on a type someone else already backs pools the reward.
                 BOUNTY LIST               — list every open bounty: mob, total reward, backers, and age.
                 BOUNTY CANCEL <mob>       — pull your own unclaimed bounty on a mob type; refunded in full.
                 When a bountied mob dies, the pooled reward pays out to the killer (split across their
                 party like a gold drop) and is announced server-wide. You cannot bounty a harmless
                 creature, and you can only cancel a stake before it is paid out.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"BOUNTY".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageBounty(args)));
    }
}
