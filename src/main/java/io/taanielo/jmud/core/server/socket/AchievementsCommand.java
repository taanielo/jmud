package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code ACHIEVEMENTS} command, showing the player which milestone achievements they
 * have unlocked (with the date/time earned) and their progress toward the ones still locked
 * (e.g. {@code 5/100 kills}).
 */
public class AchievementsCommand extends RegistrableCommand {

    public AchievementsCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "achievements";
    }

    @Override
    public String shortDescription() {
        return "Show your unlocked achievements and progress toward locked ones.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: ACHIEVEMENTS
                 Lists every milestone achievement. Unlocked ones show the date and time you
                 earned them; locked ones show your current progress (e.g. 5/100 kills).
                 Some milestones also grant a title, shown as [Title: <name> - earned/locked].
                 Unlocking such a milestone awards the title automatically; use the TITLE
                 command to display it on your WHO/SCORE line.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"ACHIEVEMENTS".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::showAchievements));
    }
}
