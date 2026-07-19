package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link SellCommand}.
 */
class SellCommandTest {

    private SellCommand command;

    @BeforeEach
    void setUp() {
        command = new SellCommand(new SocketCommandRegistry());
    }

    @Test
    void matchesSellToken() {
        assertTrue(command.match("SELL sword").isPresent());
        assertTrue(command.match("sell sword").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("buy sword").isPresent());
        assertFalse(command.match("repair sword").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void passesArgsToSingleItemSell() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        command.match("SELL iron sword").orElseThrow().execute(context);

        assertEquals("iron sword", captured.get());
        assertFalse(context.sellAllCalled, "single-item SELL must not route to sell-all");
    }

    @Test
    void sellAllRoutesToBulkSellWithNullKeyword() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        command.match("SELL all").orElseThrow().execute(context);

        assertTrue(context.sellAllCalled, "SELL ALL should route to sellAllToShop");
        assertNull(context.sellAllKeyword, "SELL ALL with no keyword passes null");
        assertNull(captured.get(), "SELL ALL must not fall through to single-item sell");
    }

    @Test
    void sellAllWithKeywordPassesKeyword() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        command.match("SELL ALL pelt").orElseThrow().execute(context);

        assertTrue(context.sellAllCalled);
        assertEquals("pelt", context.sellAllKeyword);
    }

    @Test
    void sellAllIsCaseInsensitive() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        command.match("sell ALL").orElseThrow().execute(context);

        assertTrue(context.sellAllCalled);
    }

    @Test
    void longDescriptionDocumentsSellAll() {
        assertTrue(command.longDescription().contains("SELL ALL"));
        assertTrue(command.longDescription().contains("<item name>"));
    }

    @Test
    void nameIsSell() {
        assertEquals("sell", command.name());
    }

    @Test
    void hasDescriptions() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
        assertTrue(command.longDescription().contains("SELL"));
    }

    // --- helpers ---

    private static class CapturingContext implements SocketCommandContext {
        private final AtomicReference<String> captured;
        boolean sellAllCalled;
        @Nullable String sellAllKeyword;

        CapturingContext(AtomicReference<String> captured) {
            this.captured = captured;
        }

        @Override public void sellToShop(String args) { captured.set(args); }
        @Override public void sellAllToShop(@Nullable String keyword) {
            sellAllCalled = true;
            sellAllKeyword = keyword;
        }
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
        @Override public void quaffItem(String a) {}
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
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
}
