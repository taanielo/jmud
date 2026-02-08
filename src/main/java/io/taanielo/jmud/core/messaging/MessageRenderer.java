package io.taanielo.jmud.core.messaging;

import java.util.Objects;

public class MessageRenderer {

    public String render(MessageSpec spec, MessageContext context) {
        Objects.requireNonNull(spec, "Message spec is required");
        Objects.requireNonNull(context, "Message context is required");
        String text = spec.text();
        return text
            .replace("{source}", value(context.sourceName()))
            .replace("{target}", value(context.targetName()))
            .replace("{name}", value(context.targetName()))
            .replace("{item}", value(context.itemName()))
            .replace("{effect}", value(context.effectName()))
            .replace("{ability}", value(context.abilityName()))
            .replace("{damage}", value(context.damage()));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String value(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }
}
