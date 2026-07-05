package io.taanielo.jmud.core.server.ssh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PemHostKeyProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesPemHostKeyOnFirstLoad() throws Exception {
        Path hostKeyPath = tempDir.resolve("hostkey.pem");
        PemHostKeyProvider provider = new PemHostKeyProvider(hostKeyPath);

        List<KeyPair> keys = provider.loadKeys(null);

        assertEquals(1, keys.size());
        assertTrue(Files.exists(hostKeyPath), "host key file should be created on first load");
        List<String> lines = Files.readAllLines(hostKeyPath);
        assertEquals("-----BEGIN OPENSSH PRIVATE KEY-----", lines.get(0));
        byte[] content = Files.readAllBytes(hostKeyPath);
        // Java serialization streams start with the magic bytes 0xAC 0xED — must never appear here
        assertFalse((content[0] & 0xFF) == 0xAC && (content[1] & 0xFF) == 0xED,
            "host key must not be written with Java object serialization");
    }

    @Test
    void reusesPersistedHostKeyOnSubsequentLoads() throws Exception {
        Path hostKeyPath = tempDir.resolve("hostkey.pem");

        List<KeyPair> firstBoot = new PemHostKeyProvider(hostKeyPath).loadKeys(null);
        List<KeyPair> secondBoot = new PemHostKeyProvider(hostKeyPath).loadKeys(null);

        assertEquals(1, firstBoot.size());
        assertEquals(1, secondBoot.size());
        assertArrayEquals(
            firstBoot.get(0).getPublic().getEncoded(),
            secondBoot.get(0).getPublic().getEncoded(),
            "restart must reuse the persisted host key instead of generating a new one");
    }

    @Test
    void removesLegacySerializedKeyWhenPresent() throws Exception {
        Path legacyPath = tempDir.resolve("hostkey.ser");
        Files.write(legacyPath, new byte[] {(byte)0xAC, (byte)0xED, 0x00, 0x05});

        assertTrue(PemHostKeyProvider.removeLegacySerializedKey(legacyPath));
        assertFalse(Files.exists(legacyPath), "legacy serialized key file should be deleted");
    }

    @Test
    void legacyRemovalIsNoOpWhenFileMissing() throws Exception {
        Path legacyPath = tempDir.resolve("hostkey.ser");

        assertFalse(PemHostKeyProvider.removeLegacySerializedKey(legacyPath));
    }

    @Test
    void generatesFreshKeyAfterLegacyMigration() throws Exception {
        Path hostKeyPath = tempDir.resolve("hostkey.pem");
        Path legacyPath = tempDir.resolve("hostkey.ser");
        Files.write(legacyPath, new byte[] {(byte)0xAC, (byte)0xED, 0x00, 0x05});

        PemHostKeyProvider.removeLegacySerializedKey(legacyPath);
        List<KeyPair> keys = new PemHostKeyProvider(hostKeyPath).loadKeys(null);

        assertEquals(1, keys.size());
        assertTrue(Files.exists(hostKeyPath));
        assertFalse(Files.exists(legacyPath));
        assertNotEquals(0, keys.get(0).getPublic().getEncoded().length);
    }
}
