package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link HelpCommand}.
 */
class HelpCommandTest {

    @Test
    void matchesHelpToken() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        HelpCommand command = new HelpCommand(registry);

        assertTrue(command.match("HELP").isPresent());
        assertTrue(command.match("help").isPresent());
    }

    @Test
    void matchesHAlias() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        HelpCommand command = new HelpCommand(registry);

        assertTrue(command.match("H").isPresent());
        assertTrue(command.match("h").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        HelpCommand command = new HelpCommand(registry);

        assertFalse(command.match("look").isPresent());
        assertFalse(command.match("who").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void listingIsSortedAlphabetically() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        // Register stub commands in non-alphabetical order.
        registry.register(stubCommand("zebra", "Does zebra things"));
        registry.register(stubCommand("alpha", "Does alpha things"));
        registry.register(stubCommand("mango", "Does mango things"));
        HelpCommand help = new HelpCommand(registry);

        CapturingContext context = new CapturingContext();
        help.match("HELP").get().execute(context);

        // Extract names from the output lines (skip header line).
        List<String> outputLines = context.lines;
        assertTrue(outputLines.get(0).startsWith("Available commands:"),
                "First line should be the header");

        // Command lines start with two spaces followed by the name.
        List<String> commandLines = outputLines.stream()
                .filter(l -> l.startsWith("  "))
                .toList();

        // "alpha", "help", "mango", "zebra" (help itself is registered too)
        assertEquals("alpha", commandLines.get(0).trim().split("\\s+", -1)[0]);
        assertEquals("help",  commandLines.get(1).trim().split("\\s+", -1)[0]);
        assertEquals("mango", commandLines.get(2).trim().split("\\s+", -1)[0]);
        assertEquals("zebra", commandLines.get(3).trim().split("\\s+", -1)[0]);
    }

    @Test
    void listingReflectsRegistryContents() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(stubCommand("foo", "Does foo"));
        registry.register(stubCommand("bar", "Does bar"));
        HelpCommand help = new HelpCommand(registry);

        CapturingContext context = new CapturingContext();
        help.match("HELP").get().execute(context);

        String combined = String.join("\n", context.lines);
        assertTrue(combined.contains("foo"), "Output should mention 'foo'");
        assertTrue(combined.contains("bar"), "Output should mention 'bar'");
        assertTrue(combined.contains("help"), "Output should mention 'help' itself");
    }

    @Test
    void detailShowsLongDescriptionForKnownCommand() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(stubCommand("foo", "Short foo", "Long foo description\nSecond line"));
        HelpCommand help = new HelpCommand(registry);

        CapturingContext context = new CapturingContext();
        help.match("HELP foo").get().execute(context);

        String combined = String.join("\n", context.lines);
        assertTrue(combined.contains("Long foo description"), "Should show long description");
        assertTrue(combined.contains("Second line"), "Should show all lines of long description");
    }

    @Test
    void detailReturnsErrorForUnknownCommand() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        HelpCommand help = new HelpCommand(registry);

        CapturingContext context = new CapturingContext();
        help.match("HELP unknowncmd").get().execute(context);

        assertFalse(context.promptMessage.isBlank(),
                "Should write an error message via writeLineWithPrompt");
        assertTrue(context.promptMessage.contains("unknowncmd"));
    }

    // --- helpers ---

    private static SocketCommandHandler stubCommand(String name, String shortDesc) {
        return stubCommand(name, shortDesc, shortDesc);
    }

    private static SocketCommandHandler stubCommand(String name, String shortDesc, String longDesc) {
        return new SocketCommandHandler() {
            @Override public String name() { return name; }
            @Override public String shortDescription() { return shortDesc; }
            @Override public String longDescription() { return longDesc; }
            @Override public Optional<SocketCommandMatch> match(String input) { return Optional.empty(); }
        };
    }

    private static class CapturingContext implements SocketCommandContext {
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) { lines.add(m); }
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void quaffItem(String a) {}
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
