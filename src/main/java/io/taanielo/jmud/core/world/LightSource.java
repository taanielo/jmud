package io.taanielo.jmud.core.world;

import org.jspecify.annotations.Nullable;

/**
 * Light-emission facet of an {@link Item}: the radius of light it casts while carried, letting its
 * holder see in dark rooms. A {@code null} {@link #radius()} marks a non-light-emitting item. This
 * is a construction-time value object grouping the light-source parameter so item features can grow
 * without reshaping {@link Item}'s constructor; the positivity invariant is validated in
 * {@link Item}.
 *
 * @param radius the radius of light emitted while carried, or {@code null} when the item is not a
 *               light source
 */
public record LightSource(@Nullable Integer radius) {

    private static final LightSource NONE = new LightSource(null);

    /**
     * Returns the shared "not a light source" state (no radius).
     */
    public static LightSource none() {
        return NONE;
    }

    /**
     * Returns a light source emitting the given radius.
     *
     * @param radius the radius of light emitted; must be positive
     */
    public static LightSource of(int radius) {
        return new LightSource(radius);
    }

    /**
     * Returns whether this state emits light (i.e. has a positive radius).
     */
    public boolean emitsLight() {
        return radius != null && radius > 0;
    }
}
