package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link MountCommand} and {@link DismountCommand}: verifies that {@code MOUNT <name>}
 * and {@code DISMOUNT} route to the corresponding {@link SocketCommandContext} methods, and that
 * {@code MOUNT} with no argument reports a usage message.
 */
class MountCommandTest {

    @Test
    void mountTokenRoutesToMountWithArgs() {
        MountCommand cmd = new MountCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("MOUNT sturdy pony").orElseThrow().execute(context);

        assertEquals("sturdy pony", context.mountedName);
    }

    @Test
    void mountWithoutArgsReportsUsage() {
        MountCommand cmd = new MountCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("MOUNT").orElseThrow().execute(context);

        assertNull(context.mountedName);
        assertEquals("Usage: MOUNT <name>", context.usage);
    }

    @Test
    void mountDoesNotMatchOtherTokens() {
        MountCommand cmd = new MountCommand(new SocketCommandRegistry());

        assertFalse(cmd.match("DISMOUNT").isPresent());
        assertFalse(cmd.match("MOUNTAIN").isPresent());
    }

    @Test
    void dismountTokenRoutesToDismount() {
        DismountCommand cmd = new DismountCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("DISMOUNT").orElseThrow().execute(context);

        assertTrue(context.dismounted);
    }

    @Test
    void commandNames() {
        assertEquals("mount", new MountCommand(new SocketCommandRegistry()).name());
        assertEquals("dismount", new DismountCommand(new SocketCommandRegistry()).name());
    }

    private static final class CapturingContext implements SocketCommandContext {
        String mountedName;
        boolean dismounted;
        String usage;

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { this.usage = m; }
        @Override public void writeLineSafe(String m) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void mount(String a) { this.mountedName = a; }
        @Override public void dismount(String a) { this.dismounted = true; }
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
