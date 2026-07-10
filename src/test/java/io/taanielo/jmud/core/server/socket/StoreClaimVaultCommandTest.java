package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Verifies that the STORE/CLAIM/VAULT commands parse their tokens and delegate to the correct
 * {@link SocketCommandContext} vault operations.
 */
class StoreClaimVaultCommandTest {

    @Test
    void storeCommand_matchesAndDelegatesWithArgs() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        StoreCommand command = new StoreCommand(registry);
        CapturingContext context = new CapturingContext();

        Optional<SocketCommandMatch> match = command.match("STORE a rusty sword");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("a rusty sword", context.storedItem);
    }

    @Test
    void claimCommand_matchesAndDelegatesWithArgs() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        ClaimCommand command = new ClaimCommand(registry);
        CapturingContext context = new CapturingContext();

        Optional<SocketCommandMatch> match = command.match("claim a rusty sword");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("a rusty sword", context.claimedItem);
    }

    @Test
    void vaultCommand_matchesAndDelegates() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        VaultCommand command = new VaultCommand(registry);
        CapturingContext context = new CapturingContext();

        Optional<SocketCommandMatch> match = command.match("vault");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertTrue(context.vaultListed);
    }

    @Test
    void commands_doNotMatchOtherTokens() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        assertFalse(new StoreCommand(registry).match("look").isPresent());
        assertFalse(new ClaimCommand(registry).match("deposit").isPresent());
        assertFalse(new VaultCommand(registry).match("inventory").isPresent());
    }

    private static class CapturingContext implements SocketCommandContext {
        String storedItem;
        String claimedItem;
        boolean vaultListed;

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
        @Override public void quaffItem(String a) {}
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void storeItemInBank(String a) { storedItem = a; }
        @Override public void claimItemFromBank(String a) { claimedItem = a; }
        @Override public void sendVault() { vaultListed = true; }
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
