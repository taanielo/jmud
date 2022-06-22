package io.taanielo.jmud.core.authentication;

import java.util.Objects;

import lombok.Getter;

import org.apache.commons.lang3.StringUtils;

public class Username {
    @Getter
    private final String value;
    private final String lowercaseValue;

    private Username(String value, String lowercaseValue) {
        this.value = value;
        this.lowercaseValue = lowercaseValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Username username = (Username)o;
        // username comparison should be always case-insensitive to avoid similar usernames (ex. "MadBob" vs "madbob")
        return StringUtils.equals(lowercaseValue, username.lowercaseValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowercaseValue);
    }

    public static Username of(String value) {
        return new Username(value, StringUtils.lowerCase(value));
    }
}
