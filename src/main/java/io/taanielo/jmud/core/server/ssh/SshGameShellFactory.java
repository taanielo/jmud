package io.taanielo.jmud.core.server.ssh;

import java.util.Objects;

import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.ShellFactory;

import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.socket.GameContext;

/**
 * Creates SSH shell commands bound to the game session.
 */
public class SshGameShellFactory implements ShellFactory {

    private final GameContext context;
    private final ClientPool clientPool;

    public SshGameShellFactory(GameContext context, ClientPool clientPool) {
        this.context = Objects.requireNonNull(context, "Game context is required");
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
    }

    @Override
    public Command createShell(ChannelSession channelSession) {
        return new SshGameShell(context, clientPool, channelSession.getSession());
    }
}
