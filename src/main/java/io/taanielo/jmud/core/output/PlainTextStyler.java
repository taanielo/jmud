package io.taanielo.jmud.core.output;

public class PlainTextStyler implements TextStyler {

    @Override
    public String banner(String text) {
        return text;
    }

    @Override
    public String title(String text) {
        return text;
    }

    @Override
    public String info(String text) {
        return text;
    }
}
