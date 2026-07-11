package io.taanielo.jmud.core.authentication;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

public final class Username {
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
        // Username is final, so instanceof is exact here and correctly rejects null; there are no
        // subclasses whose identity a getClass() check would need to preserve.
        if (!(o instanceof Username username))
            return false;
        // username comparison should be always case-insensitive to avoid similar usernames (ex. "MadBob" vs "madbob")
        return StringUtils.equals(lowercaseValue, username.lowercaseValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowercaseValue);
    }

    @JsonCreator
    public static Username of(String value) {
        return new Username(value, StringUtils.lowerCase(value));
    }

    @JsonValue
    public String jsonValue() {
        return value;
    }
}
