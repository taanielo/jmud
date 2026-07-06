package io.taanielo.jmud.core.world;

/**
 * The action an {@link ItemEffect} performs when the item is used.
 */
public enum ItemEffectOperation {
    /** Applies the referenced effect to the target (the default). */
    APPLY,
    /** Removes the referenced effect from the target if it is currently active (a "cure"). */
    REMOVE
}
