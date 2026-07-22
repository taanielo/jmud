package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code BOUNTY} command, the entry point to player-funded bounties on mob <em>types</em>
 * and rival <em>players</em>.
 *
 * <p>Any player may stake gold against a mob type (payable to whoever next kills a mob of that type
 * anywhere in the world) or against a specific player (payable to whoever next defeats them in a
 * consensual {@code DUEL}). Supported forms:
 * <ul>
 *   <li>{@code BOUNTY POST <mob> <gold>}    — escrow gold against a mob type; a second poster on the
 *       same type stacks as another backer, pooling the reward.</li>
 *   <li>{@code BOUNTY POST <player> <gold>} — escrow gold against a rival player, paid to whoever next
 *       beats them in a duel; if a name matches both a mob and a player, the player wins the tiebreak.</li>
 *   <li>{@code BOUNTY LIST}                 — every open bounty server-wide: type, target, total reward,
 *       backers, and age.</li>
 *   <li>{@code BOUNTY CANCEL <target>}      — pull your own unclaimed stake and get a full refund.</li>
 * </ul>
 *
 * <p>When any player kills a bountied mob type the pooled reward pays out to the killer (split across
 * their eligible party like a mob's gold drop); when a bountied player loses a duel the whole pooled
 * reward pays to the winner. Either way a server-wide announcement names the victor, the target, and
 * the total, and the paid bounties close. Command logic lives in
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
        return "Put gold on a mob type or a rival player, paid to whoever beats them next.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: BOUNTY POST <mob|player> <gold> | BOUNTY LIST | BOUNTY CANCEL <mob|player>
                 BOUNTY POST <mob> <gold>     — escrow gold against a mob type; the gold is taken from you
                                                now and paid to whoever next kills that mob type anywhere.
                                                Posting on a type someone else already backs pools the reward.
                 BOUNTY POST <player> <gold>  — escrow gold against a rival player, paid to whoever next
                                                defeats them in a consensual DUEL. It never forces a fight:
                                                the target must still ACCEPT a challenge. If a name matches
                                                both a mob and a player, it is treated as the player.
                 BOUNTY LIST                  — list every open bounty: type, target, total reward, backers,
                                                age, and how soon each one lapses.
                 BOUNTY CANCEL <mob|player>   — pull your own unclaimed bounty on that target; refunded in full.
                 When a bountied mob dies the pooled reward pays out to the killer (split across their
                 party like a gold drop); when a bountied player loses a duel the whole pool pays to the
                 winner. Either way it is announced server-wide. You cannot bounty a harmless creature or
                 yourself, and you can only cancel a stake before it is paid out.
                 A bounty left unclaimed for a while expires on its own and the full stake is refunded to
                 you, with a note sent to your mailbox. You may hold only a limited number of open
                 bounties at once — use BOUNTY CANCEL to free one up if you hit the cap.\
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
