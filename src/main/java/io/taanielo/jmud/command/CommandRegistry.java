package io.taanielo.jmud.command;

import lombok.Getter;

import io.taanielo.jmud.command.system.QuitCommand;
import io.taanielo.jmud.command.system.SayCommand;

public class CommandRegistry {
    public static final SayCommand SAY = new SayCommand();
    public static final QuitCommand QUIT = new QuitCommand();
}