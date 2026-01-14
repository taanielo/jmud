package io.taanielo.jmud.command;

import lombok.Getter;

public class AbstractCommand {

    @Getter
    private final Class<?> actClass;

    protected AbstractCommand(Class<?> actClass) {
        this.actClass = actClass;
    }

}