package io.taanielo.jmud.core.authentication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // The following tests pin the exact delimiter-splitting behaviour of parseEncoded so that the
    // switch to an explicit split limit cannot silently change how stored credentials are parsed.

    @Test
    void encodedWithTrailingDelimiterIsRejected() {
        // A trailing '$' (empty hash segment) must be rejected, not treated as a fifth empty part.
        // This is exactly the "drop trailing empty strings" behaviour of the default split limit.
        assertThrows(IllegalArgumentException.class,
            () -> Password.of("pbkdf2$sha256$1000$AAAAAAAAAAAAAAAAAAAAAA==$"));
    }

    @Test
    void encodedWithTooManyPartsIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> Password.of("pbkdf2$sha256$1000$salt$hash$extra"));
    }

    @Test
    void encodedWithTooFewPartsIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> Password.of("pbkdf2$sha256$1000$salt"));
    }
}
