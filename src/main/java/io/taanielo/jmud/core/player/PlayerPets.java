package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Holds the mob templates a player has permanently tamed as companions (see the TAME command).
 *
 * <p>Each entry is a mob-template id string. Duplicates are allowed — a player may tame two mobs of
 * the same kind. The collection is persisted on the {@link Player} so tamed companions survive
 * logout/login and are re-spawned into the world when their owner is next online.
 */
public class PlayerPets {
    private final List<String> tamedTemplateIds;

    public PlayerPets(List<String> tamedTemplateIds) {
        this.tamedTemplateIds = List.copyOf(Objects.requireNonNullElse(tamedTemplateIds, List.of()));
    }

    /**
     * Returns an empty {@link PlayerPets} instance.
     */
    public static PlayerPets empty() {
        return new PlayerPets(List.of());
    }

    /**
     * Returns the tamed mob-template ids, in the order they were tamed.
     */
    public List<String> tamedTemplateIds() {
        return tamedTemplateIds;
    }

    /**
     * Returns how many companions the player currently has tamed.
     */
    public int count() {
        return tamedTemplateIds.size();
    }

    /**
     * Returns a copy of this component with the given mob template added as a tamed companion.
     *
     * @param templateId the tamed mob's template id; must not be blank
     */
    public PlayerPets tame(String templateId) {
        Objects.requireNonNull(templateId, "templateId is required");
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must not be blank");
        }
        List<String> next = new ArrayList<>(tamedTemplateIds);
        next.add(templateId);
        return new PlayerPets(next);
    }

    /**
     * Returns a copy of this component with the first occurrence of the given template removed,
     * or this instance unchanged when the template is not present.
     *
     * @param templateId the tamed mob's template id to release
     */
    public PlayerPets release(String templateId) {
        Objects.requireNonNull(templateId, "templateId is required");
        if (!tamedTemplateIds.contains(templateId)) {
            return this;
        }
        List<String> next = new ArrayList<>(tamedTemplateIds);
        next.remove(templateId);
        return new PlayerPets(next);
    }
}
