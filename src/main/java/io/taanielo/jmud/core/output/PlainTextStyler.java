package io.taanielo.jmud.core.output;

import io.taanielo.jmud.core.world.Rarity;

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

    @Override
    public String rarity(String text, Rarity rarity) {
        return text;
    }
}
