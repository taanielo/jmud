package io.taanielo.jmud.core.server.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.session.ServerSession;

import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.connection.ClientConnection;
import io.taanielo.jmud.core.server.socket.GameContext;
import io.taanielo.jmud.core.server.socket.SocketClient;

/**
 * SSH shell command that binds the game client to an SSH channel.
 */
@Slf4j
public class SshGameShell implements Command {

    private final GameContext context;
    private final ClientPool clientPool;
    private final ServerSession session;
    private final AtomicBoolean exitSent = new AtomicBoolean();
    private InputStream input;
    private OutputStream output;
    private ExitCallback exitCallback;
    private SocketClient client;

    public SshGameShell(GameContext context, ClientPool clientPool, ServerSession session) {
        this.context = Objects.requireNonNull(context, "Game context is required");
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
        this.session = Objects.requireNonNull(session, "Server session is required");
    }

    @Override
    public void setInputStream(InputStream in) {
        this.input = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.output = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        // Game output is written to the main stream.
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.exitCallback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
        User user = session.getAttribute(SshSessionAttributes.AUTHENTICATED_USER);
        Boolean isNewUser = session.getAttribute(SshSessionAttributes.NEW_USER);
        if (user == null) {
            writeError("Authentication state missing; closing session.");
            exit(1);
            return;
        }
        ClientConnection connection = new SshClientConnection(input, output);
        AuthenticationService authService = (input, handler) -> {
            throw new IllegalStateException("SSH sessions are pre-authenticated.");
        };
        Runnable onClose = () -> exit(0);
        client = new SocketClient(
            connection,
            authService,
            context,
            clientPool,
            user,
            Boolean.TRUE.equals(isNewUser),
            onClose
        );
        clientPool.add(client);
    }

    @Override
    public void destroy(ChannelSession channel) {
        if (client != null) {
            client.close();
        } else {
            exit(0);
        }
    }

    private void exit(int code) {
        if (exitSent.compareAndSet(false, true) && exitCallback != null) {
            exitCallback.onExit(code);
        }
    }

    private void writeError(String message) {
        if (output == null) {
            return;
        }
        try {
            output.write((message + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            log.debug("Failed to write SSH error message", e);
        }
    }
}
