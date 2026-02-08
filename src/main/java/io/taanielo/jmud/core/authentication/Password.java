package io.taanielo.jmud.core.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Hashed password value object using PBKDF2-HMAC-SHA256.
 */
public final class Password {
    private static final String SCHEME = "pbkdf2";
    private static final String DIGEST = "sha256";
    private static final String DELIMITER = "$";
    private static final int DEFAULT_ITERATIONS = 310_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final int iterations;
    private final byte[] salt;
    private final byte[] hash;
    private final String encoded;

    private Password(int iterations, byte[] salt, byte[] hash) {
        this.iterations = iterations;
        this.salt = salt.clone();
        this.hash = hash.clone();
        this.encoded = encode(iterations, salt, hash);
    }

    /**
     * Returns the default PBKDF2 iteration count.
     */
    public static int defaultIterations() {
        return DEFAULT_ITERATIONS;
    }

    /**
     * Creates a password from a stored value or hashes legacy plaintext.
     */
    @JsonCreator
    public static Password of(String value) {
        Objects.requireNonNull(value, "Password value is required");
        if (value.startsWith(SCHEME + DELIMITER)) {
            return parseEncoded(value);
        }
        return hash(value, DEFAULT_ITERATIONS);
    }

    /**
     * Hashes a raw password using the default iteration count.
     */
    public static Password hash(String raw) {
        return hash(raw, DEFAULT_ITERATIONS);
    }

    /**
     * Hashes a raw password using the provided iteration count.
     */
    public static Password hash(String raw, int iterations) {
        Objects.requireNonNull(raw, "Password is required");
        if (iterations <= 0) {
            throw new IllegalArgumentException("Iterations must be positive");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = deriveKey(raw, salt, iterations);
        return new Password(iterations, salt, derived);
    }

    /**
     * Verifies a raw password against the stored hash.
     */
    public boolean matches(String raw) {
        Objects.requireNonNull(raw, "Password is required");
        byte[] derived = deriveKey(raw, salt, iterations);
        return MessageDigest.isEqual(hash, derived);
    }

    @JsonValue
    public String jsonValue() {
        return encoded;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Password password = (Password)other;
        return encoded.equals(password.encoded);
    }

    @Override
    public int hashCode() {
        return encoded.hashCode();
    }

    private static Password parseEncoded(String value) {
        String[] parts = value.split("\\$");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid password format");
        }
        if (!SCHEME.equals(parts[0]) || !DIGEST.equals(parts[1])) {
            throw new IllegalArgumentException("Unsupported password scheme");
        }
        int iterations = Integer.parseInt(parts[2]);
        byte[] salt = Base64.getDecoder().decode(parts[3]);
        byte[] hash = Base64.getDecoder().decode(parts[4]);
        return new Password(iterations, salt, hash);
    }

    private static byte[] deriveKey(String raw, byte[] salt, int iterations) {
        char[] chars = raw.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BYTES * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(int iterations, byte[] salt, byte[] hash) {
        return String.join(
            DELIMITER,
            SCHEME,
            DIGEST,
            String.valueOf(iterations),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash)
        );
    }
}
