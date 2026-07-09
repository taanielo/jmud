package io.taanielo.jmud.core.weather;

/**
 * The kinds of weather the {@link WeatherEngine} can produce. {@link #CLEAR} is the neutral state
 * with no room description or combat effect; the other three apply progressively stronger
 * visibility and combat penalties (see {@link Weather}).
 */
public enum WeatherType {

    /** No weather: outdoor rooms render normally and combat is unaffected. */
    CLEAR,

    /** Rain: a small accuracy penalty offset by a slight dodge bonus for everyone. */
    RAIN,

    /** Fog: a visibility penalty that reduces attacker accuracy. */
    FOG,

    /** Storm: a heavy visibility penalty plus an extra miss chance for ranged attacks. */
    STORM
}
