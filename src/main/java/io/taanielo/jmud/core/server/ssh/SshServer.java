package io.taanielo.jmud.core.server.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.server.socket.GameContext;

/**
 * SSH server endpoint for the game.
 */
@Slf4j
public class SshServer implements Server {

    private final int port;
    private final String host;
    private final GameContext context;
    private final ClientPool clientPool;

    public SshServer(String host, int port, GameContext context, ClientPool clientPool) {
        this.host = Objects.requireNonNull(host, "Host is required");
        this.port = port;
        this.context = Objects.requireNonNull(context, "Game context is required");
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
    }

    @Override
    public void run() {
        ensureSecurityProviders();
        log.debug("Starting SSH server @ port {}", port);
        org.apache.sshd.server.SshServer server = org.apache.sshd.server.SshServer.setUpDefaultServer();
        server.setHost(host);
        server.setPort(port);
        server.setPasswordAuthenticator(new UserRegistryPasswordAuthenticator(
            context.userRegistry(),
            context.authenticationPolicy(),
            context.authenticationLimiter()
        ));
        server.setShellFactory(new SshGameShellFactory(context, clientPool));
        configureSecurity(server);
        Path hostKeyPath = Path.of("data/ssh/hostkey.ser");
        try {
            Files.createDirectories(hostKeyPath.getParent());
            server.setKeyPairProvider(buildHostKeyProvider(hostKeyPath));
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

    private void ensureSecurityProviders() {
        if (SecurityUtils.isBouncyCastleRegistered()) {
            return;
        }
        try {
            Security.addProvider(new BouncyCastleProvider());
            if (SecurityUtils.isBouncyCastleRegistered()) {
                log.info("BouncyCastle provider registered for SSH cryptography.");
            } else {
                log.warn("BouncyCastle provider registration did not succeed.");
            }
        } catch (Exception e) {
            log.warn("Failed to register BouncyCastle provider; SSH PQ KEX may be unavailable.", e);
        }
    }

    private void configureSecurity(org.apache.sshd.server.SshServer server) {
        server.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
        server.setUserAuthFactories(List.of(UserAuthPasswordFactory.INSTANCE));
        server.setCompressionFactories(List.of(BuiltinCompressions.none));

        CoreModuleProperties.AUTH_TIMEOUT.set(server, Duration.ofSeconds(30));
        CoreModuleProperties.MAX_AUTH_REQUESTS.set(server, 3);
        CoreModuleProperties.AUTH_METHODS.set(server, UserAuthPasswordFactory.NAME);
        CoreModuleProperties.WELCOME_BANNER.set(server, "Authorized access only.");

        List<NamedFactory<Cipher>> cipherFactories = cipherFactories();
        if (!cipherFactories.isEmpty()) {
            server.setCipherFactories(cipherFactories);
        }
        List<NamedFactory<Mac>> macFactories = macFactories();
        if (!macFactories.isEmpty()) {
            server.setMacFactories(macFactories);
        }
        List<NamedFactory<Signature>> signatureFactories = signatureFactories();
        if (!signatureFactories.isEmpty()) {
            server.setSignatureFactories(signatureFactories);
        }
    }

    private SimpleGeneratorHostKeyProvider buildHostKeyProvider(Path hostKeyPath) {
        SimpleGeneratorHostKeyProvider provider = new SimpleGeneratorHostKeyProvider(hostKeyPath);
        provider.setStrictFilePermissions(true);
        if (SecurityUtils.isEDDSACurveSupported()) {
            provider.setAlgorithm("Ed25519");
        } else {
            provider.setAlgorithm("RSA");
            provider.setKeySize(3072);
        }
        return provider;
    }

    private List<NamedFactory<Cipher>> cipherFactories() {
        List<BuiltinCiphers> candidates = List.of(
            BuiltinCiphers.cc20p1305_openssh,
            BuiltinCiphers.aes128gcm,
            BuiltinCiphers.aes256gcm,
            BuiltinCiphers.aes128ctr,
            BuiltinCiphers.aes256ctr
        );
        return candidates.stream()
            .filter(BuiltinCiphers::isSupported)
            .map(factory -> (NamedFactory<Cipher>)factory)
            .toList();
    }

    private List<NamedFactory<Mac>> macFactories() {
        List<BuiltinMacs> candidates = List.of(
            BuiltinMacs.hmacsha256etm,
            BuiltinMacs.hmacsha512etm,
            BuiltinMacs.hmacsha256,
            BuiltinMacs.hmacsha512
        );
        return candidates.stream()
            .filter(BuiltinMacs::isSupported)
            .map(factory -> (NamedFactory<Mac>)factory)
            .toList();
    }

    private List<NamedFactory<Signature>> signatureFactories() {
        List<BuiltinSignatures> candidates = List.of(
            BuiltinSignatures.ed25519,
            BuiltinSignatures.rsaSHA512,
            BuiltinSignatures.rsaSHA256,
            BuiltinSignatures.nistp256,
            BuiltinSignatures.nistp384,
            BuiltinSignatures.nistp521
        );
        return candidates.stream()
            .filter(BuiltinSignatures::isSupported)
            .map(factory -> (NamedFactory<Signature>)factory)
            .toList();
    }
}
