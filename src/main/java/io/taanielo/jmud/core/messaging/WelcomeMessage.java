package io.taanielo.jmud.core.messaging;

import java.io.IOException;

import lombok.Value;

@Value(staticConstructor = "of")
public class WelcomeMessage implements Message {
    int onlineCount;

    private static final String[] BANNER = {
        " __   __  ___  _   _  ____  __  __ ",
        "|  | |  |/ _ \\| | | |/ ___||  \\/  |",
        "|  |_|  | (_) | |_| |\\___ \\| |\\/| |",
        " \\____/ \\___/ \\___/  |____/|_|  |_|"
    };

    private static final String[] DESCRIPTION = {
        "Explore a shared world of rooms, secrets, and combat.",
        "Form parties, hunt monsters, and leave your mark on Xolo."
    };

    @Override
    public void send(MessageWriter messageWriter) throws IOException {
        messageWriter.writeLine();
        messageWriter.writeLine();
        for (String line : BANNER) {
            messageWriter.writeLine(line);
        }
        messageWriter.writeLine();
        messageWriter.writeLine("Welcome to Xolo MUD!");
        for (String line : DESCRIPTION) {
            messageWriter.writeLine(line);
        }
        messageWriter.writeLine();
        messageWriter.writeLine("Players online: " + onlineCount);
        messageWriter.writeLine();
        messageWriter.write("Please enter your name: ");
    }
}
