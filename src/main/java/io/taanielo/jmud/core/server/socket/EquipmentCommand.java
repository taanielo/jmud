package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code equipment} / {@code eq} command, displaying the items a
 * player currently has worn in each equipment slot.
 */
public class EquipmentCommand extends RegistrableCommand {

    public EquipmentCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "equipment";
    }

    @Override
    public String shortDescription() {
        return "Show the items you have worn in each slot, and item-set progress. Alias: EQ";
    }

    @Override
    public String longDescription() {
        return """
               Usage: EQUIPMENT  |  EQ
                 Lists every equipment slot and the item worn there (or (empty)).

               Item sets:
                 Some gear belongs to a named item set (e.g. Wayfarer's Leathers). Wearing
                 several pieces of the same set grants a stacking bonus at defined thresholds
                 (e.g. 2pc: +2 AC, 3pc: +3 AC), added on top of each piece's own stats. Set
                 bonuses are computed from your currently-worn gear every time they are read,
                 so removing a piece immediately drops any bonus you no longer qualify for.
                 EQUIPMENT and SCORE show a line per set you have at least one piece of, e.g.
                 "Wayfarer's Leathers (2/3) - 2pc: +2 AC", so progress toward the next
                 threshold is always visible. EXAMINE <item> names an item's set and the
                 pieces that complete it, so you can plan what to hunt for next.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"EQUIPMENT".equals(token) && !"EQ".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, EquipmentCommand::handleEquipment));
    }

    private static void handleEquipment(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to check your equipment.");
            return;
        }
        context.sendEquipment();
    }
}
