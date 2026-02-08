package io.taanielo.jmud.core.server.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.server.socket.GameContext;

/**
 * SSH server endpoint for the game.
 */
@Slf4j
public class SshServer implements Server {

    private final int port;
    private final GameContext context;
    private final ClientPool clientPool;

    public SshServer(int port, GameContext context, ClientPool clientPool) {
        this.port = port;
        this.context = Objects.requireNonNull(context, "Game context is required");
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
    }

    @Override
    public void run() {
        log.debug("Starting SSH server @ port {}", port);
        org.apache.sshd.server.SshServer server = org.apache.sshd.server.SshServer.setUpDefaultServer();
        server.setPort(port);
        server.setPasswordAuthenticator(new UserRegistryPasswordAuthenticator(context.userRegistry()));
        server.setShellFactory(new SshGameShellFactory(context, clientPool));
        Path hostKeyPath = Path.of("data/ssh/hostkey.ser");
        try {
            Files.createDirectories(hostKeyPath.getParent());
            server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyPath));
            server.start();
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000L);
            }
        } catch (IOException e) {
            log.error("SSH server error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                server.stop();
            } catch (IOException e) {
                log.error("Failed to stop SSH server", e);
            }
        }
    }
}
