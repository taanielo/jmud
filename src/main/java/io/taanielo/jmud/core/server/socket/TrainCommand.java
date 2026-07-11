package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code TRAIN} command, letting players spend practice points
 * to learn new abilities from the Master Trainer in the Training Yard.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code TRAIN LIST}       — show all trainable abilities for the player's class</li>
 *   <li>{@code TRAIN <ability-id>} — spend one practice point to learn the named ability</li>
 * </ul>
 *
 * <p>The command only functions when the player is in the {@code training-yard}
 * room and the Master Trainer mob is present.
 */
public class TrainCommand extends RegistrableCommand {

    public TrainCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "train";
    }

    @Override
    public String shortDescription() {
        return "Spend practice points to learn abilities from the Master Trainer. Use TRAIN LIST to begin.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: TRAIN <sub-command> [args]
                 TRAIN LIST         \u2014 show abilities trainable by your class
                 TRAIN <id>         \u2014 spend one practice point to learn the ability (e.g. TRAIN skill.bash)
               Requires the Master Trainer to be present (Training Yard only).\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"TRAIN".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeTrain(args)));
    }
}
