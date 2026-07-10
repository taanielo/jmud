package io.taanielo.jmud.core.player;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.achievement.PlayerAchievements;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.combat.Combatant;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectTarget;
import io.taanielo.jmud.core.faction.PlayerReputation;
import io.taanielo.jmud.core.guild.GuildId;
import io.taanielo.jmud.core.quest.ActiveQuest;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;

public class Player implements EffectTarget, Combatant {
    private final PlayerIdentity identity;
    private final PlayerCombatState combatState;
    private final PlayerPreferences preferences;
    private final PlayerAbilities abilities;
    private final PlayerInventory inventory;
    private final PlayerEquipment equipment;
    /** Persisted gold balance; never negative. */
    private final int gold;
    /** In-memory only — never serialised to JSON; cleared on disconnect/reload. */
    private final boolean resting;
    /** Currently active quest contract, or {@code null} when none is held. */
    private final ActiveQuest activeQuest;
    /** Cumulative count of mobs killed; defaults to {@code 0} for existing players. */
    private final long totalKills;
    /** Unspent practice points earned by levelling up; defaults to {@code 0} for existing players. */
    private final int practicePoints;
    /** Persisted gold balance held in the bank; never negative. */
    private final int bankedGold;
    /** Titles earned by the player, e.g. via quest completion; defaults to empty for existing players. */
    private final PlayerTitles titles;
    /** Custom command aliases defined by the player; defaults to empty for existing players. */
    private final PlayerAliases aliases;
    /** Offline messages waiting in the player's mailbox; defaults to empty for existing players. */
    private final PlayerMailbox mailbox;
    /** Hunger and thirst levels (0&ndash;100); defaults to fully sated for existing players. */
    private final PlayerSustenance sustenance;
    /** Permanently tamed companion mob templates; defaults to empty for existing players. */
    private final PlayerPets pets;
    /** Signed reputation standing with each faction; defaults to empty (all neutral) for existing players. */
    private final PlayerReputation reputation;
    /** Unlocked milestone achievements with unlock timestamps; defaults to empty for existing players. */
    private final PlayerAchievements achievements;
    /** Rooms the player has previously visited, used to render their personal minimap; defaults to empty. */
    private final PlayerExploration exploration;
    /** Usernames whose TELL/SAY messages this player has muted via IGNORE; defaults to empty. */
    private final PlayerIgnoreList ignoreList;
    /** Persistent guild membership (a mirror of the authoritative roster); defaults to no guild. */
    private final PlayerGuildMembership guildMembership;
    /** Items stored in the player's personal bank vault; defaults to empty for existing players. */
    private final PlayerVault vault;

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
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
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
        ClassId classId,
        Boolean dead,
        List<Item> inventory,
        PlayerEquipment equipment,
        Integer gold,
        ActiveQuest activeQuest,
        Long totalKills,
        Integer practicePoints,
        Integer bankedGold,
        List<String> titles,
        Map<String, String> aliases,
        List<PlayerMailMessage> mailbox,
        PlayerSustenance sustenance,
        List<String> tamedPets
    ) {
        this(user, level, experience, vitals, effects, promptFormat, ansiEnabled, learnedAbilities,
            race, classId, dead, inventory, equipment, gold, activeQuest, totalKills, practicePoints,
            bankedGold, titles, aliases, mailbox, sustenance, tamedPets, null);
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
        ClassId classId,
        Boolean dead,
        List<Item> inventory,
        PlayerEquipment equipment,
        Integer gold,
        ActiveQuest activeQuest,
        Long totalKills,
        Integer practicePoints,
        Integer bankedGold,
        List<String> titles,
        Map<String, String> aliases,
        List<PlayerMailMessage> mailbox,
        PlayerSustenance sustenance,
        List<String> tamedPets,
        Map<String, Integer> reputation
    ) {
        this(user, level, experience, vitals, effects, promptFormat, ansiEnabled, learnedAbilities,
            race, classId, dead, inventory, equipment, gold, activeQuest, totalKills, practicePoints,
            bankedGold, titles, aliases, mailbox, sustenance, tamedPets, reputation, null, null, null, null, null);
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
        @JsonProperty("equipment") PlayerEquipment equipment,
        @JsonProperty("gold") Integer gold,
        @JsonProperty("activeQuest") ActiveQuest activeQuest,
        @JsonProperty("totalKills") Long totalKills,
        @JsonProperty("practicePoints") Integer practicePoints,
        @JsonProperty("bankedGold") Integer bankedGold,
        @JsonProperty("titles") List<String> titles,
        @JsonProperty("aliases") Map<String, String> aliases,
        @JsonProperty("mailbox") List<PlayerMailMessage> mailbox,
        @JsonProperty("sustenance") PlayerSustenance sustenance,
        @JsonProperty("tamedPets") List<String> tamedPets,
        @JsonProperty("reputation") Map<String, Integer> reputation,
        @JsonProperty("achievements") Map<String, String> achievements,
        @JsonProperty("explored_rooms") List<String> exploredRooms,
        @JsonProperty("ignoredPlayers") List<String> ignoredPlayers,
        @JsonProperty("guildId") String guildId,
        @JsonProperty("bankedItems") List<Item> bankedItems
    ) {
        this(
            new PlayerIdentity(user, level, experience, race, classId),
            new PlayerCombatState(vitals, effects, dead),
            new PlayerPreferences(promptFormat, ansiEnabled),
            new PlayerAbilities(learnedAbilities),
            new PlayerInventory(inventory),
            equipment == null ? PlayerEquipment.empty() : equipment,
            false,
            gold == null ? 0 : Math.max(0, gold),
            activeQuest,
            totalKills == null ? 0L : totalKills,
            practicePoints == null ? 0 : Math.max(0, practicePoints),
            bankedGold == null ? 0 : Math.max(0, bankedGold),
            new PlayerTitles(titles),
            new PlayerAliases(aliases),
            new PlayerMailbox(mailbox),
            sustenance == null ? PlayerSustenance.defaults() : sustenance,
            new PlayerPets(tamedPets),
            PlayerReputation.fromStringMap(reputation),
            PlayerAchievements.fromStringMap(achievements),
            new PlayerExploration(exploredRooms),
            new PlayerIgnoreList(ignoredPlayers == null ? List.of() : ignoredPlayers),
            PlayerGuildMembership.fromId(guildId),
            new PlayerVault(bankedItems)
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
        this(identity, combatState, preferences, abilities, inventory, equipment, false, 0, null, 0L, 0, 0, PlayerTitles.empty(), PlayerAliases.empty(), PlayerMailbox.empty(), PlayerSustenance.defaults(), PlayerPets.empty(), PlayerReputation.empty(), PlayerAchievements.empty(), PlayerExploration.empty(), PlayerIgnoreList.empty(), PlayerGuildMembership.none(), PlayerVault.empty());
    }

    private Player(
        PlayerIdentity identity,
        PlayerCombatState combatState,
        PlayerPreferences preferences,
        PlayerAbilities abilities,
        PlayerInventory inventory,
        PlayerEquipment equipment,
        boolean resting,
        int gold
    ) {
        this(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, null, 0L, 0, 0, PlayerTitles.empty(), PlayerAliases.empty(), PlayerMailbox.empty(), PlayerSustenance.defaults(), PlayerPets.empty(), PlayerReputation.empty(), PlayerAchievements.empty(), PlayerExploration.empty(), PlayerIgnoreList.empty(), PlayerGuildMembership.none(), PlayerVault.empty());
    }

    private Player(
        PlayerIdentity identity,
        PlayerCombatState combatState,
        PlayerPreferences preferences,
        PlayerAbilities abilities,
        PlayerInventory inventory,
        PlayerEquipment equipment,
        boolean resting,
        int gold,
        ActiveQuest activeQuest
    ) {
        this(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, 0L, 0, 0, PlayerTitles.empty(), PlayerAliases.empty(), PlayerMailbox.empty(), PlayerSustenance.defaults(), PlayerPets.empty(), PlayerReputation.empty(), PlayerAchievements.empty(), PlayerExploration.empty(), PlayerIgnoreList.empty(), PlayerGuildMembership.none(), PlayerVault.empty());
    }

    private Player(
        PlayerIdentity identity,
        PlayerCombatState combatState,
        PlayerPreferences preferences,
        PlayerAbilities abilities,
        PlayerInventory inventory,
        PlayerEquipment equipment,
        boolean resting,
        int gold,
        ActiveQuest activeQuest,
        long totalKills
    ) {
        this(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, 0, 0, PlayerTitles.empty(), PlayerAliases.empty(), PlayerMailbox.empty(), PlayerSustenance.defaults(), PlayerPets.empty(), PlayerReputation.empty(), PlayerAchievements.empty(), PlayerExploration.empty(), PlayerIgnoreList.empty(), PlayerGuildMembership.none(), PlayerVault.empty());
    }

    private Player(
        PlayerIdentity identity,
        PlayerCombatState combatState,
        PlayerPreferences preferences,
        PlayerAbilities abilities,
        PlayerInventory inventory,
        PlayerEquipment equipment,
        boolean resting,
        int gold,
        ActiveQuest activeQuest,
        long totalKills,
        int practicePoints,
        int bankedGold,
        PlayerTitles titles,
        PlayerAliases aliases,
        PlayerMailbox mailbox,
        PlayerSustenance sustenance,
        PlayerPets pets,
        PlayerReputation reputation,
        PlayerAchievements achievements,
        PlayerExploration exploration,
        PlayerIgnoreList ignoreList,
        PlayerGuildMembership guildMembership,
        PlayerVault vault
    ) {
        this.identity = Objects.requireNonNull(identity, "Identity is required");
        this.combatState = Objects.requireNonNull(combatState, "Combat state is required");
        this.preferences = Objects.requireNonNull(preferences, "Preferences are required");
        this.abilities = Objects.requireNonNull(abilities, "Abilities are required");
        this.inventory = Objects.requireNonNull(inventory, "Inventory is required");
        this.equipment = Objects.requireNonNull(equipment, "Equipment is required");
        this.gold = Math.max(0, gold);
        this.resting = resting;
        this.activeQuest = activeQuest;
        this.totalKills = Math.max(0L, totalKills);
        this.practicePoints = Math.max(0, practicePoints);
        this.bankedGold = Math.max(0, bankedGold);
        this.titles = Objects.requireNonNullElse(titles, PlayerTitles.empty());
        this.aliases = Objects.requireNonNullElse(aliases, PlayerAliases.empty());
        this.mailbox = Objects.requireNonNullElse(mailbox, PlayerMailbox.empty());
        this.sustenance = Objects.requireNonNullElse(sustenance, PlayerSustenance.defaults());
        this.pets = Objects.requireNonNullElse(pets, PlayerPets.empty());
        this.reputation = Objects.requireNonNullElse(reputation, PlayerReputation.empty());
        this.achievements = Objects.requireNonNullElse(achievements, PlayerAchievements.empty());
        this.exploration = Objects.requireNonNullElse(exploration, PlayerExploration.empty());
        this.ignoreList = Objects.requireNonNullElse(ignoreList, PlayerIgnoreList.empty());
        this.guildMembership = Objects.requireNonNullElse(guildMembership, PlayerGuildMembership.none());
        this.vault = Objects.requireNonNullElse(vault, PlayerVault.empty());
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

    @JsonProperty("gold")
    public int getGold() {
        return gold;
    }

    @JsonProperty("activeQuest")
    public ActiveQuest getActiveQuest() {
        return activeQuest;
    }

    @JsonProperty("totalKills")
    public long getTotalKills() {
        return totalKills;
    }

    @JsonProperty("practicePoints")
    public int getPracticePoints() {
        return practicePoints;
    }

    @JsonProperty("bankedGold")
    public int getBankedGold() {
        return bankedGold;
    }

    @JsonProperty("titles")
    public List<String> getTitles() {
        return titles.earned();
    }

    @JsonProperty("aliases")
    public Map<String, String> getAliases() {
        return aliases.expansions();
    }

    @JsonProperty("mailbox")
    public List<PlayerMailMessage> getMailbox() {
        return mailbox.messages();
    }

    @JsonProperty("sustenance")
    public PlayerSustenance getSustenance() {
        return sustenance;
    }

    @JsonProperty("tamedPets")
    public List<String> getTamedPets() {
        return pets.tamedTemplateIds();
    }

    @JsonProperty("reputation")
    public Map<String, Integer> getReputation() {
        return reputation.toStringMap();
    }

    @JsonProperty("achievements")
    public Map<String, String> getAchievements() {
        return achievements.toStringMap();
    }

    @JsonProperty("explored_rooms")
    public List<String> getExploredRooms() {
        return exploration.toIdList();
    }

    @JsonProperty("ignoredPlayers")
    public List<String> getIgnoredPlayers() {
        return ignoreList.ignoredNames().stream().sorted().toList();
    }

    /**
     * Returns the persisted guild id string for JSON serialisation, or {@code null} when the player
     * belongs to no guild (in which case the field is omitted from existing/new save files).
     */
    @JsonProperty("guildId")
    public String getGuildId() {
        GuildId id = guildMembership.guildId();
        return id == null ? null : id.value();
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

    public PlayerTitles titles() {
        return titles;
    }

    public PlayerAliases aliases() {
        return aliases;
    }

    public PlayerMailbox mailbox() {
        return mailbox;
    }

    public PlayerSustenance sustenance() {
        return sustenance;
    }

    @JsonIgnore
    public PlayerPets pets() {
        return pets;
    }

    /**
     * Returns this player's faction reputation standings.
     */
    @JsonIgnore
    public PlayerReputation reputation() {
        return reputation;
    }

    /**
     * Returns this player's unlocked milestone achievements.
     */
    @JsonIgnore
    public PlayerAchievements achievements() {
        return achievements;
    }

    /**
     * Returns this player's room-exploration state (the rooms they have visited).
     */
    @JsonIgnore
    public PlayerExploration exploration() {
        return exploration;
    }

    /**
     * Returns this player's ignore list (players whose TELL/SAY they have muted).
     */
    @JsonIgnore
    public PlayerIgnoreList ignoreList() {
        return ignoreList;
    }

    /**
     * Returns this player's persistent guild membership (a mirror of the authoritative guild roster).
     */
    @JsonIgnore
    public PlayerGuildMembership guildMembership() {
        return guildMembership;
    }

    /**
     * Returns the items stored in this player's bank vault, for JSON serialisation.
     *
     * <p>Vaulted items are never part of carried inventory while stored and survive death,
     * corpse decay and corpse looting.
     */
    @JsonProperty("bankedItems")
    public List<Item> getBankedItems() {
        return vault.items();
    }

    /**
     * Returns this player's bank vault component.
     */
    @JsonIgnore
    public PlayerVault vault() {
        return vault;
    }

    /**
     * Returns a copy of this player with the given vault replacing the current one.
     *
     * @param newVault the new vault state; must not be null
     */
    public Player withVault(PlayerVault newVault) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, Objects.requireNonNull(newVault, "Vault is required"));
    }

    /**
     * Returns a copy of this player with the given item added to their bank vault.
     *
     * @param item the item to store; must not be null
     */
    public Player addBankedItem(Item item) {
        return withVault(vault.addItem(item));
    }

    /**
     * Returns a copy of this player with the first vault item matching the given item's id removed.
     *
     * @param item the item to remove from the vault; must not be null
     */
    public Player removeBankedItem(Item item) {
        return withVault(vault.removeItem(item));
    }

    /**
     * Returns a copy of this player with the given guild membership.
     *
     * @param newGuildMembership the new guild membership; must not be null (use
     *                           {@link PlayerGuildMembership#none()} to clear)
     */
    public Player withGuildMembership(PlayerGuildMembership newGuildMembership) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, Objects.requireNonNull(newGuildMembership, "Guild membership is required"), vault);
    }

    /**
     * Returns a copy of this player with the given ignore list.
     *
     * @param newIgnoreList the new ignore-list state; must not be null
     */
    public Player withIgnoreList(PlayerIgnoreList newIgnoreList) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, Objects.requireNonNull(newIgnoreList, "Ignore list is required"), guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given room marked as explored, or this instance
     * unchanged when the room was already visited.
     *
     * @param roomId the room the player has entered; must not be null
     */
    public Player exploreRoom(RoomId roomId) {
        PlayerExploration updated = exploration.visit(roomId);
        if (updated == exploration) {
            return this;
        }
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, updated, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given unlocked-achievements component.
     *
     * @param newAchievements the new achievements state; must not be null
     */
    public Player withAchievements(PlayerAchievements newAchievements) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, Objects.requireNonNull(newAchievements, "Achievements are required"), exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given reputation standings.
     *
     * @param newReputation the new reputation state; must not be null
     */
    public Player withReputation(PlayerReputation newReputation) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, Objects.requireNonNull(newReputation, "Reputation is required"), achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given tamed-companion collection.
     *
     * @param newPets the new tamed-pet state; must not be null
     */
    public Player withTamedPets(PlayerPets newPets) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, Objects.requireNonNull(newPets, "Pets are required"), reputation, achievements, exploration, ignoreList, guildMembership, vault);
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

    /**
     * Returns {@code true} while the player is hidden in stealth (via SNEAK/HIDE).
     *
     * <p>This flag is in-memory only and is never persisted to JSON, so it is always
     * {@code false} after a disconnect or server restart.
     */
    @JsonIgnore
    public boolean isStealthActive() {
        return combatState.stealthActive();
    }

    @Override
    public List<EffectInstance> effects() {
        return combatState.effects();
    }

    /** {@inheritDoc} Tick-thread only (AGENTS.md §5). */
    @Override
    public void addEffect(EffectInstance instance) {
        combatState.addEffect(instance);
    }

    /** {@inheritDoc} Tick-thread only (AGENTS.md §5). */
    @Override
    public boolean removeEffect(EffectInstance instance) {
        return combatState.removeEffect(instance);
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
        return new Player(identity, combatState.die(), preferences, abilities, inventory, equipment, false, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player respawn() {
        return new Player(identity, combatState.respawn(), preferences, abilities, inventory, equipment, false, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player withoutEffects() {
        return new Player(identity, combatState.withoutEffects(), preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player withAnsiEnabled(boolean enabled) {
        return new Player(identity, combatState, preferences.withAnsiEnabled(enabled), abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given prompt format string, preserving all other state.
     *
     * @param nextFormat the new prompt format (token substitution handled by {@code PromptRenderer})
     * @return an updated player
     */
    public Player withPromptFormat(String nextFormat) {
        return new Player(identity, combatState, preferences.withPromptFormat(nextFormat), abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player withVitals(PlayerVitals updatedVitals) {
        return new Player(identity, combatState.withVitals(updatedVitals), preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player withDead(boolean dead) {
        return new Player(identity, combatState.withDead(dead), preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player withLearnedAbilities(List<AbilityId> learnedAbilities) {
        return new Player(identity, combatState, preferences, abilities.withLearned(learnedAbilities), inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given titles replacing the current set.
     *
     * @param earnedTitles the new titles list
     */
    public Player withTitles(List<String> earnedTitles) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles.withEarned(earnedTitles), aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given title granted, unless it was
     * already earned, in which case this instance is returned unchanged.
     *
     * @param title the title to grant; must not be null
     */
    public Player grantTitle(String title) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles.grant(title), aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given alias defined or overwritten.
     *
     * @param name      the alias name; case-insensitive, must not be blank
     * @param expansion the command line the alias expands to; must not be blank
     */
    public Player defineAlias(String name, String expansion) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases.define(name, expansion), mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given alias removed, unchanged if it did
     * not exist.
     *
     * @param name the alias name to remove; case-insensitive
     */
    public Player removeAlias(String name) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases.remove(name), mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given mailbox replacing the current one.
     *
     * @param newMailbox the new mailbox state
     */
    public Player withMailbox(PlayerMailbox newMailbox) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, newMailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given sustenance (hunger/thirst) state.
     *
     * @param newSustenance the new sustenance state; must not be null
     */
    public Player withSustenance(PlayerSustenance newSustenance) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, Objects.requireNonNull(newSustenance, "Sustenance is required"), pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player withInventory(List<Item> items) {
        return new Player(identity, combatState, preferences, abilities, inventory.withItems(items), equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player addItem(Item item) {
        return new Player(identity, combatState, preferences, abilities, inventory.addItem(item), equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player removeItem(Item item) {
        return new Player(identity, combatState, preferences, abilities, inventory.removeItem(item), equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    public Player withEquipment(PlayerEquipment nextEquipment) {
        return new Player(identity, combatState, preferences, abilities, inventory, nextEquipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given identity (level and experience).
     */
    public Player withIdentity(PlayerIdentity nextIdentity) {
        return new Player(nextIdentity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the resting flag set to the given value.
     *
     * <p>The resting state is in-memory only and is never persisted.
     *
     * @param resting {@code true} to enter a resting state, {@code false} to wake up
     */
    public Player withResting(boolean resting) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the stealth (hidden) flag set to the given value.
     *
     * <p>The stealth state is in-memory only and is never persisted.
     *
     * @param active {@code true} to enter stealth, {@code false} to leave it
     */
    public Player withStealth(boolean active) {
        return new Player(identity, combatState.withStealth(active), preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given gold balance.
     *
     * @param newGold the new gold amount; negative values are clamped to 0
     */
    public Player withGold(int newGold) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, newGold, activeQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given amount of gold added.
     *
     * @param amount amount to add; may be negative (clamped so balance never goes below 0)
     */
    public Player addGold(int amount) {
        return withGold(gold + amount);
    }

    /**
     * Returns a copy of this player with the given banked gold balance.
     *
     * @param newBankedGold the new banked gold amount; negative values are clamped to 0
     */
    public Player withBankedGold(int newBankedGold) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, practicePoints, newBankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given amount added to the banked gold balance.
     *
     * @param amount amount to add; may be negative (clamped so balance never goes below 0)
     */
    public Player addBankedGold(int amount) {
        return withBankedGold(bankedGold + amount);
    }

    /**
     * Returns a copy of this player with the given active quest set.
     *
     * @param quest the quest to set, or {@code null} to clear the active quest
     */
    public Player withActiveQuest(ActiveQuest quest) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, quest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the total kill count set to the given value.
     *
     * @param newTotalKills the new kill count; negative values are clamped to 0
     */
    public Player withTotalKills(long newTotalKills) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, newTotalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a copy of this player with the given practice point balance.
     *
     * @param newPracticePoints the new practice point count; negative values are clamped to 0
     */
    public Player withPracticePoints(int newPracticePoints) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, totalKills, newPracticePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, guildMembership, vault);
    }

    /**
     * Returns a fully independent copy of this player, safe to hand to another thread
     * (e.g. the persistence write-behind queue).
     *
     * <p>Every other component of {@code Player} is immutable, but the active effects
     * held in {@link PlayerCombatState} are mutable ({@link EffectInstance#tickDown()}
     * and friends run every tick), so a plain field copy would let a background writer
     * observe a mid-tick mutation. This method deep-copies the effect list so the
     * returned snapshot is stable regardless of subsequent tick-thread activity on
     * the original instance.
     *
     * @return an independent snapshot of this player's current state
     */
    public Player snapshotForPersistence() {
        List<EffectInstance> effectsCopy = combatState.effects().stream().map(EffectInstance::copy).toList();
        return new Player(
            identity.user(),
            identity.level(),
            identity.experience(),
            combatState.vitals(),
            effectsCopy,
            preferences.promptFormat(),
            preferences.ansiEnabled(),
            abilities.learned(),
            identity.race(),
            identity.classId(),
            combatState.dead(),
            inventory.items(),
            equipment,
            gold,
            activeQuest,
            totalKills,
            practicePoints,
            bankedGold,
            titles.earned(),
            aliases.expansions(),
            mailbox.messages(),
            sustenance,
            pets.tamedTemplateIds(),
            reputation.toStringMap(),
            achievements.toStringMap(),
            exploration.toIdList(),
            getIgnoredPlayers(),
            getGuildId(),
            vault.items()
        );
    }
}
