package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Container facet of an {@link Item}: whether it can hold other items, how many, what it currently
 * holds, and whether it is locked. A {@code null} {@link #capacity()} marks a non-container item;
 * a positive capacity marks a bag/chest/strongbox. This is a construction-time value object grouping
 * the container-related parameters so item features can grow without reshaping {@link Item}'s
 * constructor; validation of the invariants (capacity positive, contents within capacity, no nested
 * containers, locking only for containers) lives in {@link Item}.
 *
 * @param capacity max slots when this item is a container, or {@code null} for a normal item
 * @param contents the items currently held inside; always empty for non-containers
 * @param locked   whether the container is locked, blocking access to its contents
 */
public record ContainerState(@Nullable Integer capacity, List<Item> contents, boolean locked) {

    private static final ContainerState NONE = new ContainerState(null, List.of(), false);

    public ContainerState {
        contents = List.copyOf(Objects.requireNonNullElse(contents, List.of()));
    }

    /**
     * Returns the shared "not a container" state (no capacity, empty, unlocked).
     */
    public static ContainerState none() {
        return NONE;
    }

    /**
     * Returns an empty, unlocked container with the given capacity.
     *
     * @param capacity the maximum number of slots; must be positive
     */
    public static ContainerState of(int capacity) {
        return new ContainerState(capacity, List.of(), false);
    }

    /**
     * Returns an unlocked container with the given capacity and contents.
     *
     * @param capacity the maximum number of slots; must be positive
     * @param contents the items currently held inside
     */
    public static ContainerState of(int capacity, List<Item> contents) {
        return new ContainerState(capacity, contents, false);
    }

    /**
     * Returns whether this state describes a container (i.e. has a capacity).
     */
    public boolean isContainer() {
        return capacity != null;
    }
}
