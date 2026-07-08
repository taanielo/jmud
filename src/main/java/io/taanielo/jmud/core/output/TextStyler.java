package io.taanielo.jmud.core.output;

import io.taanielo.jmud.core.world.Rarity;

public interface TextStyler {
    String banner(String text);

    String title(String text);

    String info(String text);

    /**
     * Renders an item name colored by its rarity tier. Common items are returned unstyled; higher
     * tiers may be wrapped in a tier-specific color by ANSI-capable implementations. Plain
     * implementations return {@code text} unchanged for every tier.
     *
     * @param text   the item name to render
     * @param rarity the item's rarity tier
     * @return the styled (or plain) item name
     */
    String rarity(String text, Rarity rarity);
}
