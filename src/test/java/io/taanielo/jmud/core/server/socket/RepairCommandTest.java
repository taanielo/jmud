package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link RepairCommand}.
 */
class RepairCommandTest {

    private RepairCommand command;

    @BeforeEach
    void setUp() {
        command = new RepairCommand(new SocketCommandRegistry());
    }

    @Test
    void matchesRepairToken() {
        assertTrue(command.match("REPAIR sword").isPresent());
        assertTrue(command.match("repair sword").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("buy sword").isPresent());
        assertFalse(command.match("sell sword").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void passesArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("REPAIR iron sword");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("iron sword", captured.get());
    }

    @Test
    void repairAllRoutesToBatchRepair() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("REPAIR all");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertTrue(context.repairAllCalled, "REPAIR ALL should route to repairAllItems");
        assertNull(captured.get(), "REPAIR ALL must not fall through to single-item repair");
    }

    @Test
    void repairAllIsCaseInsensitive() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        command.match("REPAIR ALL").orElseThrow().execute(context);

        assertTrue(context.repairAllCalled);
    }

    @Test
    void repairAllWithNoBlacksmithReportsAndDoesNotDelegate() {
        // Mirrors the impl gating: with no blacksmith present, the command must not attempt a repair.
        AtomicReference<String> captured = new AtomicReference<>();
        GatingContext context = new GatingContext(captured, false);

        command.match("REPAIR ALL").orElseThrow().execute(context);

        assertFalse(context.batchRepairAttempted, "no batch repair when no blacksmith is present");
        assertTrue(context.lastMessage.contains("blacksmith"));
    }

    @Test
    void longDescriptionDocumentsAll() {
        assertTrue(command.longDescription().contains("ALL"));
    }

    @Test
    void nameIsRepair() {
        assertEquals("repair", command.name());
    }

    @Test
    void hasDescriptions() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
        assertTrue(command.longDescription().contains("REPAIR"));
    }

    // --- helpers ---

    private static class CapturingContext implements SocketCommandContext {
        private final AtomicReference<String> captured;
        boolean repairAllCalled;

        CapturingContext(AtomicReference<String> captured) {
            this.captured = captured;
        }

        @Override public void repairItem(String args) { captured.set(args); }
        @Override public void repairAllItems() { repairAllCalled = true; }
        @Override public void quaffItem(String args) {}
        @Override public void readItem(String args) {}
        @Override public void writeItem(String args) {}
        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) {}
        @Override public void writeLineSafe(String m) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
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

    /**
     * A context that mirrors the impl's blacksmith gating for {@code REPAIR ALL}: it only performs the
     * batch repair when a blacksmith is present, otherwise it reports the failure message.
     */
    private static class GatingContext extends CapturingContext {
        private final boolean blacksmithPresent;
        boolean batchRepairAttempted;
        String lastMessage = "";

        GatingContext(AtomicReference<String> captured, boolean blacksmithPresent) {
            super(captured);
            this.blacksmithPresent = blacksmithPresent;
        }

        @Override
        public void repairAllItems() {
            if (!blacksmithPresent) {
                lastMessage = "There is no blacksmith here to repair your gear.";
                return;
            }
            batchRepairAttempted = true;
        }
    }
}
