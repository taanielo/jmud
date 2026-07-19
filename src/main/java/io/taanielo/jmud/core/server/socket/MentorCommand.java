package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code MENTOR} command family: an opt-in bond in which a veteran player boosts a
 * partied newcomer's XP (see issue #751).
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code MENTOR <player>}   — propose a mentor bond to a newcomer in your room</li>
 *   <li>{@code MENTOR ACCEPT}    — accept a pending proposal</li>
 *   <li>{@code MENTOR DECLINE}   — decline a pending proposal</li>
 *   <li>{@code MENTOR STATUS}    — show your current bond (partner, role, bonus, since)</li>
 *   <li>{@code MENTOR END}       — end your bond (either side may, unilaterally)</li>
 * </ul>
 *
 * <p>This command performs parsing only; all validation and state changes run on the tick thread via
 * {@link SocketCommandContext#executeMentor(String)} (AGENTS.md §5).
 */
public class MentorCommand extends RegistrableCommand {

    /**
     * Creates a {@code MentorCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public MentorCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "mentor";
    }

    @Override
    public String shortDescription() {
        return "Bond with a newcomer to boost their XP, or manage your mentor bond.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: MENTOR [sub-command] [args]
                 MENTOR <player>   — propose a mentor bond to a newcomer in your room
                 MENTOR ACCEPT     — accept a pending mentor proposal
                 MENTOR DECLINE    — decline a pending mentor proposal
                 MENTOR STATUS     — show your current bond (partner, role, bonus, since)
                 MENTOR END        — end your mentor bond (either side may, at any time)

               A mentor bond lets a veteran player help a newcomer. To propose, you must be at least
               10 levels above the target, the target must be below level 20, and neither of you may
               already have a mentor bond. A proposal must be accepted within 60 seconds or it lapses.

               While mentor and mentee are grouped in the same party and both share in a mob kill, the
               mentee earns a flat +20% bonus to their own XP share — added on top, never taken from
               anyone else. A mentee graduates automatically once they reach the lesser of the mentor's
               level minus 10 or level 20, ending the bond for both of you.

               Graduating mentees advances you up the Mentors' Guild: each rank grants a lasting title
               ("the Mentor", "the Seasoned Mentor", "the Master Mentor", "the Grandmaster Mentor" at
               1/3/5/10 lifetime graduations) and a growing guild perk — a bonus to your OWN XP share
               (+5/10/15/20%) while you are grouped with your current mentee. MENTOR STATUS shows your
               rank, active perk, and progress to the next rung.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"MENTOR".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeMentor(args)));
    }
}
