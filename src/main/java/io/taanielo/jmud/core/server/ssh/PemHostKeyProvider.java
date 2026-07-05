package io.taanielo.jmud.core.server.ssh;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Generating SSH host key provider that persists the key pair in the standard OpenSSH PEM
 * format ({@code -----BEGIN OPENSSH PRIVATE KEY-----}) instead of Java object serialization.
 *
 * <p>Unlike {@link org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider}, which
 * writes the key pair with {@link java.io.ObjectOutputStream} (an opaque format and a known
 * deserialization-attack surface), this provider writes a key file that is inspectable and
 * manageable with standard tooling such as {@code ssh-keygen}. Reading is handled by the
 * inherited {@code doReadKeyPairs}, which parses PEM/OpenSSH resources via
 * {@link SecurityUtils#loadKeyPairIdentities}.
 *
 * <p>The key algorithm is Ed25519 when EdDSA support is available, otherwise RSA-3072.
 */
@Slf4j
public class PemHostKeyProvider extends AbstractGeneratorHostKeyProvider {

    private static final String KEY_COMMENT = "jmud-host-key";

    /**
     * Creates a provider that loads the host key from {@code hostKeyPath}, generating and
     * persisting a fresh key pair in OpenSSH PEM format if the file does not exist yet.
     *
     * @param hostKeyPath location of the PEM host key file, e.g. {@code data/ssh/hostkey.pem}
     */
    public PemHostKeyProvider(Path hostKeyPath) {
        setPath(hostKeyPath);
        setStrictFilePermissions(true);
        if (SecurityUtils.isEDDSACurveSupported()) {
            setAlgorithm("Ed25519");
        } else {
            setAlgorithm("RSA");
            setKeySize(3072);
        }
    }

    @Override
    protected void doWriteKeyPair(NamedResource resourceKey, KeyPair keyPair, OutputStream outputStream)
        throws IOException, GeneralSecurityException {
        OpenSSHKeyPairResourceWriter.INSTANCE
            .writePrivateKey(keyPair, KEY_COMMENT, (OpenSSHKeyEncryptionContext) null, outputStream);
        log.info("Persisted SSH host key in OpenSSH PEM format (algorithm: {})", getAlgorithm());
    }

    /**
     * Deletes a legacy Java-serialized host key file ({@code hostkey.ser}) if one exists.
     * Removing the legacy key triggers generation of a fresh PEM key on the next load, which
     * means SSH clients will see a changed-host-key warning once.
     *
     * @param legacyKeyPath path of the legacy serialized key file
     * @return {@code true} if a legacy key file was found and removed
     * @throws IOException if the file exists but cannot be deleted
     */
    public static boolean removeLegacySerializedKey(Path legacyKeyPath) throws IOException {
        boolean removed = Files.deleteIfExists(legacyKeyPath);
        if (removed) {
            log.info("Removed legacy Java-serialized SSH host key {}; "
                + "a PEM host key will be used instead (clients may see a changed-host-key warning once)", legacyKeyPath);
        }
        return removed;
    }
}
