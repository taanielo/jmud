package io.taanielo.jmud.core.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class UsernameTest {

    @Test
    void equalsIsCaseInsensitive() {
        assertEquals(Username.of("MadBob"), Username.of("madbob"));
        assertEquals(Username.of("madbob").hashCode(), Username.of("MadBob").hashCode());
    }

    @Test
    void differentNamesAreNotEqual() {
        assertNotEquals(Username.of("alice"), Username.of("bob"));
    }

    @Test
    void isReflexive() {
        Username name = Username.of("Alice");
        assertEquals(name, name);
    }

    @Test
    void isNotEqualToNull() {
        assertNotEquals(null, Username.of("alice"));
    }

    @Test
    void isNotEqualToOtherType() {
        assertNotEquals("alice", Username.of("alice"));
    }
}
