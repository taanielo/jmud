package io.taanielo.jmud.core.messaging;

import java.io.IOException;

import lombok.Value;

import io.taanielo.jmud.core.output.TextStyler;

/**
 * Banner-only welcome message without input prompt.
 */
@Value(staticConstructor = "of")
public class WelcomeBannerMessage implements Message {
    TextStyler textStyler;
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
            messageWriter.writeLine(textStyler.banner(line));
        }
        messageWriter.writeLine();
        messageWriter.writeLine(textStyler.title("Welcome to Xolo MUD!"));
        for (String line : DESCRIPTION) {
            messageWriter.writeLine(textStyler.info(line));
        }
        messageWriter.writeLine();
        messageWriter.writeLine(textStyler.info("Players online: " + onlineCount));
        messageWriter.writeLine();
    }
}
