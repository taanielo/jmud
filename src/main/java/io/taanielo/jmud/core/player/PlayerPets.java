package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Holds the mobs a player has permanently tamed as companions (see the TAME command).
 *
 * <p>Each entry pairs a mob-template id with an optional custom display name (see the NAME command)
 * and an optional custom roleplay description (see the DESCRIBE command). Duplicates are allowed — a
 * player may tame two mobs of the same kind, and a custom name lets them tell such companions apart.
 * The collection is persisted on the {@link Player} so tamed companions (and their names and
 * descriptions) survive logout/login and are re-spawned into the world when their owner is next
 * online.
 *
 * <p>The persisted JSON shape is three parallel lists: {@code tamedPets} (template ids, the original
 * bare-string list), an optional {@code tamedPetNames} (custom names, {@code null} where a companion
 * is unnamed), and an optional {@code tamedPetDescriptions} (custom descriptions, {@code null} where
 * a companion has no custom description). Older save files without the optional lists load as
 * all-unnamed / all-undescribed, so this component is backward-compatible with pre-naming and
 * pre-describe player records.
 */
public class PlayerPets {

    /**
     * A single tamed companion: its mob-template id plus an optional custom display name and an
     * optional custom roleplay description.
     *
     * @param templateId        the tamed mob's template id; must not be blank
     * @param customName        the owner-assigned display name, or {@code null} when the companion is
     *                          unnamed
     * @param customDescription the owner-assigned roleplay description shown on LOOK, or {@code null}
     *                          when the companion has no custom description
     */
    public record TamedPet(
        String templateId, @Nullable String customName, @Nullable String customDescription) {
        public TamedPet {
            Objects.requireNonNull(templateId, "templateId is required");
            if (templateId.isBlank()) {
                throw new IllegalArgumentException("templateId must not be blank");
            }
            if (customName != null && customName.isBlank()) {
                customName = null;
            }
            if (customDescription != null && customDescription.isBlank()) {
                customDescription = null;
            }
        }

        /** Returns the custom display name if one is set, otherwise empty. */
        public Optional<String> name() {
            return Optional.ofNullable(customName);
        }

        /** Returns the custom roleplay description if one is set, otherwise empty. */
        public Optional<String> description() {
            return Optional.ofNullable(customDescription);
        }
    }

    private final List<TamedPet> pets;

    private PlayerPets(List<TamedPet> pets) {
        this.pets = List.copyOf(Objects.requireNonNullElse(pets, List.of()));
    }

    /**
     * Returns an empty {@link PlayerPets} instance.
     */
    public static PlayerPets empty() {
        return new PlayerPets(List.of());
    }

    /**
     * Reconstructs a {@link PlayerPets} from its persisted parallel lists.
     *
     * @param tamedTemplateIds the tamed mob-template ids, in tame order (may be {@code null}/empty)
     * @param customNames      the parallel custom names ({@code null} entries for unnamed companions);
     *                         may be {@code null}/shorter than {@code tamedTemplateIds} for older save
     *                         files, in which case the missing names default to unnamed
     * @param customDescriptions the parallel custom descriptions ({@code null} entries for companions
     *                         with no custom description); may be {@code null}/shorter than
     *                         {@code tamedTemplateIds} for older save files, in which case the missing
     *                         descriptions default to none
     * @return the reconstructed component
     */
    public static PlayerPets fromPersisted(
        @Nullable List<String> tamedTemplateIds,
        @Nullable List<String> customNames,
        @Nullable List<String> customDescriptions) {
        List<String> ids = Objects.requireNonNullElse(tamedTemplateIds, List.of());
        List<String> names = Objects.requireNonNullElse(customNames, List.of());
        List<String> descriptions = Objects.requireNonNullElse(customDescriptions, List.of());
        List<TamedPet> entries = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String name = i < names.size() ? names.get(i) : null;
            String description = i < descriptions.size() ? descriptions.get(i) : null;
            entries.add(new TamedPet(ids.get(i), name, description));
        }
        return new PlayerPets(entries);
    }

    /**
     * Returns the tamed mob-template ids, in the order they were tamed. Retained for the many callers
     * that only need template identity (spawning, counting) and for backward-compatible persistence.
     */
    public List<String> tamedTemplateIds() {
        return pets.stream().map(TamedPet::templateId).toList();
    }

    /**
     * Returns the parallel custom names, in tame order, with a {@code null} element for each unnamed
     * companion. Used for persistence alongside {@link #tamedTemplateIds()}.
     */
    public List<String> customNames() {
        List<String> names = new ArrayList<>(pets.size());
        for (TamedPet pet : pets) {
            names.add(pet.customName());
        }
        return names;
    }

    /**
     * Returns the parallel custom descriptions, in tame order, with a {@code null} element for each
     * companion that has no custom description. Used for persistence alongside
     * {@link #tamedTemplateIds()}.
     */
    public List<String> customDescriptions() {
        List<String> descriptions = new ArrayList<>(pets.size());
        for (TamedPet pet : pets) {
            descriptions.add(pet.customDescription());
        }
        return descriptions;
    }

    /**
     * Returns the tamed companion entries (template id + optional custom name), in tame order.
     */
    public List<TamedPet> entries() {
        return pets;
    }

    /**
     * Returns how many companions the player currently has tamed.
     */
    public int count() {
        return pets.size();
    }

    /**
     * Returns a copy of this component with the given mob template added as a new, unnamed companion.
     *
     * @param templateId the tamed mob's template id; must not be blank
     */
    public PlayerPets tame(String templateId) {
        Objects.requireNonNull(templateId, "templateId is required");
        if (templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must not be blank");
        }
        List<TamedPet> next = new ArrayList<>(pets);
        next.add(new TamedPet(templateId, null, null));
        return new PlayerPets(next);
    }

    /**
     * Returns a copy of this component with the first occurrence of the given template removed,
     * or this instance unchanged when the template is not present. Preserves the existing "first
     * occurrence" release semantics.
     *
     * @param templateId the tamed mob's template id to release
     */
    public PlayerPets release(String templateId) {
        return release(templateId, null);
    }

    /**
     * Returns a copy of this component with one occurrence of the given template removed, preferring
     * the entry whose custom name matches {@code customName} so a named companion's death releases the
     * correct record among same-template duplicates. Falls back to the first template match when no
     * entry carries that name, and returns this instance unchanged when the template is absent.
     *
     * @param templateId the tamed mob's template id to release
     * @param customName the custom name of the companion being released, or {@code null} when unnamed
     */
    public PlayerPets release(String templateId, @Nullable String customName) {
        Objects.requireNonNull(templateId, "templateId is required");
        int match = -1;
        int fallback = -1;
        for (int i = 0; i < pets.size(); i++) {
            TamedPet pet = pets.get(i);
            if (!pet.templateId().equals(templateId)) {
                continue;
            }
            if (fallback < 0) {
                fallback = i;
            }
            if (Objects.equals(pet.customName(), customName)) {
                match = i;
                break;
            }
        }
        int removeIndex = match >= 0 ? match : fallback;
        if (removeIndex < 0) {
            return this;
        }
        List<TamedPet> next = new ArrayList<>(pets);
        next.remove(removeIndex);
        return new PlayerPets(next);
    }

    /**
     * Returns a copy of this component with a custom name assigned to one companion of the given
     * template. When {@code previousName} is non-{@code null} the entry currently carrying that name
     * is renamed (overwrite); otherwise the first as-yet-unnamed companion of the template is named.
     * Returns this instance unchanged when no matching companion exists.
     *
     * @param templateId   the template of the companion to name
     * @param previousName the companion's current custom name when renaming, or {@code null} to name
     *                     the first unnamed companion of the template
     * @param newName      the new custom name to assign; must not be blank
     */
    public PlayerPets withName(String templateId, @Nullable String previousName, String newName) {
        Objects.requireNonNull(templateId, "templateId is required");
        Objects.requireNonNull(newName, "newName is required");
        if (newName.isBlank()) {
            throw new IllegalArgumentException("newName must not be blank");
        }
        int target = -1;
        for (int i = 0; i < pets.size(); i++) {
            TamedPet pet = pets.get(i);
            if (!pet.templateId().equals(templateId)) {
                continue;
            }
            if (previousName != null) {
                if (previousName.equals(pet.customName())) {
                    target = i;
                    break;
                }
            } else if (pet.customName() == null) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            return this;
        }
        List<TamedPet> next = new ArrayList<>(pets);
        // Preserve any existing custom description when renaming.
        next.set(target, new TamedPet(templateId, newName, pets.get(target).customDescription()));
        return new PlayerPets(next);
    }

    /**
     * Returns a copy of this component with a custom description assigned to (or cleared from) one
     * companion of the given template. The target companion is matched by its current custom name:
     * when {@code customName} is non-{@code null} the entry carrying that name is updated, otherwise
     * the first entry of the template that is still unnamed is updated. The companion's custom name is
     * preserved. Returns this instance unchanged when no matching companion exists.
     *
     * @param templateId     the template of the companion to describe
     * @param customName     the companion's current custom name, or {@code null} to target the first
     *                       unnamed companion of the template
     * @param newDescription the new custom description to assign, or {@code null}/blank to clear it
     */
    public PlayerPets withDescription(
        String templateId, @Nullable String customName, @Nullable String newDescription) {
        Objects.requireNonNull(templateId, "templateId is required");
        int target = -1;
        for (int i = 0; i < pets.size(); i++) {
            TamedPet pet = pets.get(i);
            if (!pet.templateId().equals(templateId)) {
                continue;
            }
            if (customName != null) {
                if (customName.equals(pet.customName())) {
                    target = i;
                    break;
                }
            } else if (pet.customName() == null) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            return this;
        }
        List<TamedPet> next = new ArrayList<>(pets);
        // Preserve the existing custom name; a blank/null description clears it via the record's canonical
        // constructor normalisation.
        next.set(target, new TamedPet(templateId, pets.get(target).customName(), newDescription));
        return new PlayerPets(next);
    }

    /**
     * Finds the custom name of the first companion whose custom name (case-insensitively) equals or
     * begins with {@code token}, or empty when none match. Used to resolve a NAME/target token that
     * refers to a companion by its custom name rather than its template name.
     *
     * @param token the input token to match against custom names
     * @return the matching companion's custom name, or empty
     */
    public Optional<String> findCustomNameMatching(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (TamedPet pet : pets) {
            String name = pet.customName();
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals(normalized) || lower.startsWith(normalized)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }
}
