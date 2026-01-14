package io.taanielo.jmud.command;

public interface Command<T> {
    CommandInput<T> act();
}