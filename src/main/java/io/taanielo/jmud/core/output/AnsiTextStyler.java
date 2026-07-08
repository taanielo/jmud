package io.taanielo.jmud.core.output;

import io.taanielo.jmud.core.world.Rarity;

public class AnsiTextStyler implements TextStyler {
    private static final String RESET = "[0m";
    private static final String BOLD_CYAN = "[1;36m";
    private static final String BOLD_GREEN = "[1;32m";
    private static final String YELLOW = "[33m";
    private static final String GREEN = "[32m";
    private static final String CYAN = "[36m";

    @Override
    public String banner(String text) {
        return wrap(BOLD_CYAN, text);
    }

    @Override
    public String title(String text) {
        return wrap(BOLD_GREEN, text);
    }

    @Override
    public String info(String text) {
        return wrap(YELLOW, text);
    }

    @Override
    public String rarity(String text, Rarity rarity) {
        return switch (rarity) {
            case COMMON -> text;
            case UNCOMMON -> wrap(GREEN, text);
            case RARE -> wrap(CYAN, text);
        };
    }

    private String wrap(String prefix, String text) {
        return prefix + text + RESET;
    }
}
