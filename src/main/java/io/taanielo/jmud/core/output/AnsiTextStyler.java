package io.taanielo.jmud.core.output;

public class AnsiTextStyler implements TextStyler {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD_CYAN = "\u001B[1;36m";
    private static final String BOLD_GREEN = "\u001B[1;32m";
    private static final String YELLOW = "\u001B[33m";

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

    private String wrap(String prefix, String text) {
        return prefix + text + RESET;
    }
}
