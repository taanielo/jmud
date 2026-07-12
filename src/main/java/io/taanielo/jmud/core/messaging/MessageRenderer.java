package io.taanielo.jmud.core.messaging;

import java.util.Objects;

/**
 * Substitutes placeholders in a {@link MessageSpec} template against a {@link MessageContext}.
 *
 * <p>The {@code {verb}} placeholder carries the worded-damage tier and is conjugation-aware per
 * channel: the {@code TARGET} channel (the victim's own line) uses the second-person / base form,
 * while {@code SELF} and {@code ROOM} use the third-person-singular form. When no verb is present in
 * the context, {@code {verb}} renders as an empty string.
 */
public class MessageRenderer {

    public String render(MessageSpec spec, MessageContext context) {
        Objects.requireNonNull(spec, "Message spec is required");
        Objects.requireNonNull(context, "Message context is required");
        String verb = spec.channel() == MessageChannel.TARGET
            ? context.verbSecondPerson()
            : context.verbThirdPerson();
        String text = spec.text();
        return text
            .replace("{source}", value(context.sourceName()))
            .replace("{target}", value(context.targetName()))
            .replace("{name}", value(context.targetName()))
            .replace("{item}", value(context.itemName()))
            .replace("{effect}", value(context.effectName()))
            .replace("{ability}", value(context.abilityName()))
            .replace("{verb}", value(verb))
            .replace("{damage}", value(context.damage()));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String value(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }
}
