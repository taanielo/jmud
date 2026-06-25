package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Handles the {@code score} / {@code sc} command, displaying the player's level,
 * experience, XP needed for the next level, and current vitals.
 */
public class ScoreCommand extends RegistrableCommand {

    public ScoreCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "score";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"SCORE".equals(token) && !"SC".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, ScoreCommand::handleScore));
    }

    private static void handleScore(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to view your score.");
            return;
        }
        Player player = context.getPlayer();
        PlayerVitals vitals = player.getVitals();
        int level = player.getLevel();
        long xp = player.getExperience();
        long xpNeeded = LevelUpService.xpForNextLevel(level);
        long xpRemaining = Math.max(0L, xpNeeded - xp);

        context.writeLineSafe("--- Score ---");
        context.writeLineSafe(String.format("Level : %d", level));
        context.writeLineSafe(String.format("XP    : %d / %d  (%d to next level)", xp, xpNeeded, xpRemaining));
        context.writeLineSafe(String.format("HP    : %d / %d", vitals.hp(), vitals.maxHp()));
        context.writeLineSafe(String.format("Mana  : %d / %d", vitals.mana(), vitals.maxMana()));
        context.writeLineSafe(String.format("Move  : %d / %d", vitals.move(), vitals.maxMove()));
        context.sendPrompt();
    }
}
