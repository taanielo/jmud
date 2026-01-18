package io.taanielo.jmud.core.output;

public final class TextStylers {
    private TextStylers() {
    }

    public static TextStyler forEnabled(boolean enabled) {
        return enabled ? new AnsiTextStyler() : new PlainTextStyler();
    }
}
