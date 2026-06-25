package io.taanielo.jmud.core.player;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.combat.Combatant;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectTarget;
import io.taanielo.jmud.core.world.Item;

public class Player implements EffectTarget, Combatant {
    private final PlayerIdentity identity;
    private final PlayerCombatState combatState;
    private final PlayerPreferences preferences;
    private final PlayerAbilities abilities;
    private final PlayerInventory inventory;
    private final PlayerEquipment equipment;
    /** In-memory only — never serialised to JSON; cleared on disconnect/reload. */
    private final boolean resting;

    public static Player of(User user, String promptFormat) {
        return new Player(user, 1, 0, PlayerVitals.defaults(), List.of(), promptFormat, false, List.of(), null, null);
    }

    public static Player of(User user, String promptFormat, boolean ansiEnabled) {
        return new Player(user, 1, 0, PlayerVitals.defaults(), List.of(), promptFormat, ansiEnabled, List.of(), null, null);
    }

    public static Player of(User user, String promptFormat, boolean ansiEnabled, List<AbilityId> learnedAbilities) {
        return new Player(user, 1, 0, PlayerVitals.defaults(), List.of(), promptFormat, ansiEnabled, learnedAbilities, null, null);
    }

    public Player(
        User user,
        int level,
        long experience,
        PlayerVitals vitals,
        List<EffectInstance> effects,
        String promptFormat,
        Boolean ansiEnabled,
        List<AbilityId> learnedAbilities,
        RaceId race,
        ClassId classId
    ) {
        this(
            user,
            level,
            experience,
            vitals,
            effects,
            promptFormat,
            ansiEnabled,
            learnedAbilities,
            race,
            classId,
            false,
            null,
            null
        );
    }

    @JsonCreator
    public Player(
        @JsonProperty("user") User user,
        @JsonProperty("level") int level,
        @JsonProperty("experience") long experience,
        @JsonProperty("vitals") PlayerVitals vitals,
        @JsonProperty("effects") List<EffectInstance> effects,
        @JsonProperty("promptFormat") String promptFormat,
        @JsonProperty("ansiEnabled") Boolean ansiEnabled,
        @JsonProperty("learnedAbilities") List<AbilityId> learnedAbilities,
        @JsonProperty("race") RaceId race,
        @JsonProperty("class") ClassId classId,
        @JsonProperty("dead") Boolean dead,
        @JsonProperty("inventory") List<Item> inventory,
        @JsonProperty("equipment") PlayerEquipment equipment
    ) {
        this(
            new PlayerIdentity(user, level, experience, race, classId),
            new PlayerCombatState(vitals, effects, dead),
            new PlayerPreferences(promptFormat, ansiEnabled),
            new PlayerAbilities(learnedAbilities),
            new PlayerInventory(inventory),
            equipment == null ? PlayerEquipment.empty() : equipment
        );
    }

    private Player(
        PlayerIdentity identity,
        PlayerCombatState combatState,
        PlayerPreferences preferences,
        PlayerAbilities abilities,
        PlayerInventory inventory,
        PlayerEquipment equipment
    ) {
        this(identity, combatState, preferences, abilities, inventory, equipment, false);
    }

    private Player(
        PlayerIdentity identity,
        PlayerCombatState combatState,
        PlayerPreferences preferences,
        PlayerAbilities abilities,
        PlayerInventory inventory,
        PlayerEquipment equipment,
        boolean resting
    ) {
        this.identity = Objects.requireNonNull(identity, "Identity is required");
        this.combatState = Objects.requireNonNull(combatState, "Combat state is required");
        this.preferences = Objects.requireNonNull(preferences, "Preferences are required");
        this.abilities = Objects.requireNonNull(abilities, "Abilities are required");
        this.inventory = Objects.requireNonNull(inventory, "Inventory is required");
        this.equipment = Objects.requireNonNull(equipment, "Equipment is required");
        this.resting = resting;
    }

    @JsonProperty("user")
    public User getUser() {
        return identity.user();
    }

    @JsonProperty("level")
    public int getLevel() {
        return identity.level();
    }

    @JsonProperty("experience")
    public long getExperience() {
        return identity.experience();
    }

    @JsonProperty("vitals")
    public PlayerVitals getVitals() {
        return combatState.vitals();
    }

    @JsonProperty("effects")
    public List<EffectInstance> getEffects() {
        return combatState.effects();
    }

    @JsonProperty("promptFormat")
    public String getPromptFormat() {
        return preferences.promptFormat();
    }

    @JsonProperty("ansiEnabled")
    public boolean isAnsiEnabled() {
        return preferences.ansiEnabled();
    }

    @JsonProperty("learnedAbilities")
    public List<AbilityId> getLearnedAbilities() {
        return abilities.learned();
    }

    @JsonProperty("race")
    public RaceId getRace() {
        return identity.race();
    }

    @JsonProperty("class")
    public ClassId getClassId() {
        return identity.classId();
    }

    @JsonProperty("dead")
    public boolean isDead() {
        return combatState.dead();
    }

    @JsonProperty("inventory")
    public List<Item> getInventory() {
        return inventory.items();
    }

    @JsonProperty("equipment")
    public PlayerEquipment getEquipment() {
        return equipment;
    }

    public PlayerIdentity identity() {
        return identity;
    }

    public PlayerCombatState combatState() {
        return combatState;
    }

    public PlayerPreferences preferences() {
        return preferences;
    }

    public PlayerAbilities abilities() {
        return abilities;
    }

    public PlayerInventory inventory() {
        return inventory;
    }

    public PlayerEquipment equipment() {
        return equipment;
    }

    @JsonIgnore
    public Username getUsername() {
        return identity.user().getUsername();
    }

    @JsonIgnore
    public Password getPassword() {
        return identity.user().getPassword();
    }

    /**
     * Returns {@code true} while the player is in a resting state.
     *
     * <p>This flag is in-memory only and is never persisted to JSON,
     * so it is always {@code false} after a disconnect or server restart.
     */
    @JsonIgnore
    public boolean isResting() {
        return resting;
    }

    @Override
    public List<EffectInstance> effects() {
        return combatState.effects();
    }

    @Override
    public Username username() {
        return getUsername();
    }

    public Player die() {
        if (combatState.dead() && combatState.vitals().hp() <= 0 && combatState.effects().isEmpty()) {
            return this;
        }
        // Dying always clears the resting flag.
        return new Player(identity, combatState.die(), preferences, abilities, inventory, equipment, false);
    }

    public Player respawn() {
        return new Player(identity, combatState.respawn(), preferences, abilities, inventory, equipment, false);
    }

    public Player withoutEffects() {
        return new Player(identity, combatState.withoutEffects(), preferences, abilities, inventory, equipment, resting);
    }

    public Player withAnsiEnabled(boolean enabled) {
        return new Player(identity, combatState, preferences.withAnsiEnabled(enabled), abilities, inventory, equipment, resting);
    }

    public Player withVitals(PlayerVitals updatedVitals) {
        return new Player(identity, combatState.withVitals(updatedVitals), preferences, abilities, inventory, equipment, resting);
    }

    public Player withDead(boolean dead) {
        return new Player(identity, combatState.withDead(dead), preferences, abilities, inventory, equipment, resting);
    }

    public Player withLearnedAbilities(List<AbilityId> learnedAbilities) {
        return new Player(identity, combatState, preferences, abilities.withLearned(learnedAbilities), inventory, equipment, resting);
    }

    public Player withInventory(List<Item> items) {
        return new Player(identity, combatState, preferences, abilities, inventory.withItems(items), equipment, resting);
    }

    public Player addItem(Item item) {
        return new Player(identity, combatState, preferences, abilities, inventory.addItem(item), equipment, resting);
    }

    public Player removeItem(Item item) {
        return new Player(identity, combatState, preferences, abilities, inventory.removeItem(item), equipment, resting);
    }

    public Player withEquipment(PlayerEquipment nextEquipment) {
        return new Player(identity, combatState, preferences, abilities, inventory, nextEquipment, resting);
    }

    /**
     * Returns a copy of this player with the given identity (level and experience).
     */
    public Player withIdentity(PlayerIdentity nextIdentity) {
        return new Player(nextIdentity, combatState, preferences, abilities, inventory, equipment, resting);
    }

    /**
     * Returns a copy of this player with the resting flag set to the given value.
     *
     * <p>The resting state is in-memory only and is never persisted.
     *
     * @param resting {@code true} to enter a resting state, {@code false} to wake up
     */
    public Player withResting(boolean resting) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting);
    }
}
