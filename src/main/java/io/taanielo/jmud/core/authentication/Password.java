package io.taanielo.jmud.core.authentication;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import lombok.AccessLevel;
import lombok.Getter;

import org.apache.commons.lang3.StringUtils;

public class Password {
    @Getter(AccessLevel.NONE)
    byte[] value;

    private Password(byte[] value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Password password = (Password)o;
        return Arrays.equals(value, password.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    public static Password of(String value) {
        return new Password(StringUtils.getBytes(value, StandardCharsets.UTF_8));
    }
}
