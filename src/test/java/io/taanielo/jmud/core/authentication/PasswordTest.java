package io.taanielo.jmud.core.authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordTest {

    @Test
    void hashesAndMatches() {
        Password password = Password.hash("secret", 1000);
        assertTrue(password.matches("secret"));
        assertFalse(password.matches("wrong"));
    }

    @Test
    void parsesEncodedPassword() {
        Password password = Password.hash("secret", 1000);
        Password parsed = Password.of(password.jsonValue());
        assertTrue(parsed.matches("secret"));
    }

    @Test
    void legacyPlaintextIsHashed() {
        Password password = Password.of("legacy");
        assertTrue(password.matches("legacy"));
        assertTrue(password.jsonValue().startsWith("pbkdf2$sha256$"));
    }
}
