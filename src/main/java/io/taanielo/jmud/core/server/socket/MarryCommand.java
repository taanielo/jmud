package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code MARRY} command, a purely-opt-in roleplay bond between two players.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code MARRY <player>}            — propose marriage to a player in your room</li>
 *   <li>{@code MARRY ACCEPT}             — accept a pending proposal</li>
 *   <li>{@code MARRY DECLINE}            — decline a pending proposal</li>
 *   <li>{@code MARRY DIVORCE}            — end your marriage (either spouse may, unilaterally)</li>
 *   <li>{@code MARRY TELL <message>}     — privately message your spouse anywhere (see SPOUSETELL)</li>
 *   <li>{@code MARRY} / {@code MARRY STATUS} — show your spouse or pending proposal state</li>
 * </ul>
 *
 * <p>Marriage is mechanically inert: no stat bonus, shared bank, or combat buff. This command
 * performs parsing only; all validation and state changes run on the tick thread via
 * {@link SocketCommandContext#executeMarry(String)} (AGENTS.md §5).
 */
public class MarryCommand extends RegistrableCommand {

    /**
     * Creates a {@code MarryCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public MarryCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "marry";
    }

    @Override
    public String shortDescription() {
        return "Propose marriage, or manage your marriage bond.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: MARRY [sub-command] [args]
                 MARRY <player>        — propose marriage to a player in your room
                 MARRY ACCEPT          — accept a pending marriage proposal
                 MARRY DECLINE         — decline a pending marriage proposal
                 MARRY DIVORCE         — end your marriage (either spouse may, at any time)
                 MARRY TELL <message>  — privately message your spouse anywhere (alias: SPOUSETELL)
                 MARRY STATUS          — show your spouse or any pending proposal

               A proposal must be accepted within 60 seconds or it lapses. You may only have one
               spouse at a time. Marriage is roleplay flavour only — it grants no stats, gold, or
               combat benefit — and your spouse is shown in WHO and SCORE. Married players may
               SPOUSETELL each other from anywhere in the world, bypassing IGNORE; use DIVORCE to end
               the bond.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"MARRY".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeMarry(args)));
    }
}
