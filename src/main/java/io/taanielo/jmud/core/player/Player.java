package io.taanielo.jmud.core.player;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.achievement.PlayerAchievements;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.combat.Combatant;
import io.taanielo.jmud.core.craft.PlayerProficiencies;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectTarget;
import io.taanielo.jmud.core.faction.PlayerReputation;
import io.taanielo.jmud.core.guild.GuildId;
import io.taanielo.jmud.core.quest.ActiveQuest;
import io.taanielo.jmud.core.quest.QuestId;
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
    /** Currently active story quest contract (QUEST ACCEPT), or {@code null} when none is held. */
    private final ActiveQuest activeQuest;
    /** Currently active daily quest (DAILY_QUEST ACCEPT), held independently of {@link #activeQuest}; {@code null} when none. */
    private final ActiveQuest activeDailyQuest;
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
    /** Usernames this player has added to their buddy list via FRIEND; defaults to empty. */
    private final PlayerFriendList friendList;
    /** Persistent guild membership (a mirror of the authoritative roster); defaults to no guild. */
    private final PlayerGuildMembership guildMembership;
    /** Items stored in the player's personal bank vault; defaults to empty for existing players. */
    private final PlayerVault vault;
    /** One-time quest contracts the player has already completed; defaults to empty for existing players. */
    private final PlayerCompletedQuests completedQuests;
    /** Duels won by combat resolution; never negative, defaults to {@code 0} for existing players. */
    private final int duelWins;
    /** Duels lost by combat resolution; never negative, defaults to {@code 0} for existing players. */
    private final int duelLosses;
    /** Crafting proficiency points per profession; defaults to empty (all level 0) for existing players. */
    private final PlayerProficiencies proficiencies;
    /** Auction House keywords the player is watching via AUCTION WATCH; defaults to empty for existing players. */
    private final PlayerAuctionWatchList auctionWatchList;

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
            bankedGold, titles, aliases, mailbox, sustenance, tamedPets, reputation, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
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
        @JsonProperty("friends") List<String> friends,
        @JsonProperty("guildId") String guildId,
        @JsonProperty("bankedItems") List<Item> bankedItems,
        @JsonProperty("activeTitle") String activeTitle,
        @JsonProperty("completedQuests") List<String> completedQuests,
        @JsonProperty("duelWins") Integer duelWins,
        @JsonProperty("duelLosses") Integer duelLosses,
        @JsonProperty("proficiencies") Map<String, Integer> proficiencies,
        @JsonProperty("autoLootEnabled") Boolean autoLootEnabled,
        @JsonProperty("description") String description,
        @JsonProperty("tamedPetNames") List<String> tamedPetNames,
        @JsonProperty("briefModeEnabled") Boolean briefModeEnabled,
        @JsonProperty("activeDailyQuest") ActiveQuest activeDailyQuest,
        @JsonProperty("tamedPetDescriptions") List<String> tamedPetDescriptions,
        @JsonProperty("vaultTier") Integer vaultTier,
        @JsonProperty("spouse") String spouse,
        @JsonProperty("boundRoomId") String boundRoomId,
        @JsonProperty("autoAssistEnabled") Boolean autoAssistEnabled,
        @JsonProperty("auctionWatches") List<String> auctionWatches
    ) {
        this(
            new PlayerIdentity(user, level, experience, race, classId, description, spouse, boundRoomId),
            new PlayerCombatState(vitals, effects, dead),
            new PlayerPreferences(promptFormat, ansiEnabled, autoLootEnabled, briefModeEnabled, autoAssistEnabled),
            new PlayerAbilities(learnedAbilities),
            new PlayerInventory(inventory),
            equipment == null ? PlayerEquipment.empty() : equipment,
            false,
            gold == null ? 0 : Math.max(0, gold),
            activeQuest,
            activeDailyQuest,
            totalKills == null ? 0L : totalKills,
            practicePoints == null ? 0 : Math.max(0, practicePoints),
            bankedGold == null ? 0 : Math.max(0, bankedGold),
            new PlayerTitles(titles, activeTitle),
            new PlayerAliases(aliases),
            new PlayerMailbox(mailbox),
            sustenance == null ? PlayerSustenance.defaults() : sustenance,
            PlayerPets.fromPersisted(tamedPets, tamedPetNames, tamedPetDescriptions),
            PlayerReputation.fromStringMap(reputation),
            PlayerAchievements.fromStringMap(achievements),
            new PlayerExploration(exploredRooms),
            new PlayerIgnoreList(ignoredPlayers == null ? List.of() : ignoredPlayers),
            new PlayerFriendList(friends == null ? List.of() : friends),
            PlayerGuildMembership.fromId(guildId),
            new PlayerVault(bankedItems, vaultTier == null ? 0 : Math.max(0, vaultTier)),
            new PlayerCompletedQuests(completedQuests),
            duelWins == null ? 0 : Math.max(0, duelWins),
            duelLosses == null ? 0 : Math.max(0, duelLosses),
            PlayerProficiencies.fromStringMap(proficiencies),
            new PlayerAuctionWatchList(auctionWatches == null ? List.of() : auctionWatches)
        );
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
        ActiveQuest activeDailyQuest,
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
        PlayerFriendList friendList,
        PlayerGuildMembership guildMembership,
        PlayerVault vault,
        PlayerCompletedQuests completedQuests,
        int duelWins,
        int duelLosses,
        PlayerProficiencies proficiencies,
        PlayerAuctionWatchList auctionWatchList
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
        this.activeDailyQuest = activeDailyQuest;
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
        this.friendList = Objects.requireNonNullElse(friendList, PlayerFriendList.empty());
        this.guildMembership = Objects.requireNonNullElse(guildMembership, PlayerGuildMembership.none());
        this.vault = Objects.requireNonNullElse(vault, PlayerVault.empty());
        this.completedQuests = Objects.requireNonNullElse(completedQuests, PlayerCompletedQuests.empty());
        this.duelWins = Math.max(0, duelWins);
        this.duelLosses = Math.max(0, duelLosses);
        this.proficiencies = Objects.requireNonNullElse(proficiencies, PlayerProficiencies.empty());
        this.auctionWatchList = Objects.requireNonNullElse(auctionWatchList, PlayerAuctionWatchList.empty());
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

    @Override
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

    @JsonProperty("autoLootEnabled")
    public boolean isAutoLootEnabled() {
        return preferences.autoLootEnabled();
    }

    @JsonProperty("briefModeEnabled")
    public boolean isBriefModeEnabled() {
        return preferences.briefModeEnabled();
    }

    @JsonProperty("autoAssistEnabled")
    public boolean isAutoAssistEnabled() {
        return preferences.autoAssistEnabled();
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

    /**
     * Returns the player's custom LOOK description for JSON serialisation, or {@code null} when none
     * is set (in which case the field is omitted from save files for backward compatibility).
     */
    @JsonProperty("description")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDescription() {
        String description = identity.description();
        return description.isEmpty() ? null : description;
    }

    /**
     * Returns the username of this player's spouse (see the MARRY command) for JSON serialisation, or
     * {@code null} when the player is unmarried (in which case the field is omitted from save files, so
     * existing saves deserialise unaffected).
     */
    @JsonProperty("spouse")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSpouse() {
        return identity.spouse();
    }

    /**
     * Returns the id (value form) of the room this player has bound their recall/respawn point to via
     * the BIND command for JSON serialisation, or {@code null} when they have never bound (in which
     * case the field is omitted from save files, so existing saves deserialise unaffected and keep
     * recalling to the default starting room).
     */
    @JsonProperty("boundRoomId")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getBoundRoomId() {
        return identity.boundRoomId();
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

    /**
     * Returns this player's active daily quest (accepted via {@code DAILY_QUEST ACCEPT}), or
     * {@code null} when none is held. This slot is independent of {@link #getActiveQuest()}, so a
     * player may hold one story quest and one daily quest at the same time. Omitted from save files
     * when empty for backward compatibility with older saves.
     */
    @JsonProperty("activeDailyQuest")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ActiveQuest getActiveDailyQuest() {
        return activeDailyQuest;
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

    /**
     * Returns the player's currently active (displayed) title for JSON serialisation, or
     * {@code null} when none is selected (in which case the field is omitted from save files).
     */
    @JsonProperty("activeTitle")
    public String getActiveTitle() {
        return titles.active();
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

    /**
     * Returns the parallel custom names of the tamed companions (see the NAME command), with a
     * {@code null} entry for each unnamed companion, for JSON serialisation alongside
     * {@link #getTamedPets()}. Older save files omit this property and load as all-unnamed.
     */
    @JsonProperty("tamedPetNames")
    public List<String> getTamedPetNames() {
        return pets.customNames();
    }

    /**
     * Returns the parallel custom descriptions of the tamed companions (see the DESCRIBE command),
     * with a {@code null} entry for each companion that has no custom description, for JSON
     * serialisation alongside {@link #getTamedPets()}. Older save files omit this property and load as
     * all-undescribed.
     */
    @JsonProperty("tamedPetDescriptions")
    public List<String> getTamedPetDescriptions() {
        return pets.customDescriptions();
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
     * Returns the persisted friend usernames for JSON serialisation, sorted for stable output.
     */
    @JsonProperty("friends")
    public List<String> getFriends() {
        return friendList.friendNames().stream().sorted().toList();
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
     * Returns this player's friends list (players they have added via FRIEND).
     */
    @JsonIgnore
    public PlayerFriendList friendList() {
        return friendList;
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
     * Returns this player's purchased vault-capacity tier, for JSON serialisation.
     *
     * <p>Defaults to {@code 0} for existing saves (today's flat capacity) and only ever grows as the
     * player buys {@code VAULT UPGRADE} tiers.
     */
    @JsonProperty("vaultTier")
    public int getVaultTier() {
        return vault.capacityTier();
    }

    /**
     * Returns this player's bank vault component.
     */
    @JsonIgnore
    public PlayerVault vault() {
        return vault;
    }

    /**
     * Returns the ids of the one-time quests this player has completed, for JSON serialisation.
     */
    @JsonProperty("completedQuests")
    public List<String> getCompletedQuests() {
        return completedQuests.toIdList();
    }

    /**
     * Returns this player's completed one-time quest contracts.
     */
    @JsonIgnore
    public PlayerCompletedQuests completedQuests() {
        return completedQuests;
    }

    /**
     * Returns a copy of this player with the given one-time quest marked completed, or this instance
     * unchanged when the quest was already recorded.
     *
     * @param questId the completed one-time quest; must not be null
     */
    // Identity comparison is intentional: withCompleted returns the same instance (this) when the
    // quest was already recorded, so reference identity is the no-op sentinel we test for here.
    @SuppressWarnings("ReferenceEquality")
    public Player withCompletedQuest(QuestId questId) {
        PlayerCompletedQuests updated = completedQuests.withCompleted(questId);
        if (updated == completedQuests) {
            return this;
        }
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, updated, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given vault replacing the current one.
     *
     * @param newVault the new vault state; must not be null
     */
    public Player withVault(PlayerVault newVault) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, Objects.requireNonNull(newVault, "Vault is required"), completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
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
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, Objects.requireNonNull(newGuildMembership, "Guild membership is required"), vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given ignore list.
     *
     * @param newIgnoreList the new ignore-list state; must not be null
     */
    public Player withIgnoreList(PlayerIgnoreList newIgnoreList) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, Objects.requireNonNull(newIgnoreList, "Ignore list is required"), friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given friends list.
     *
     * @param newFriendList the new friends-list state; must not be null
     */
    public Player withFriendList(PlayerFriendList newFriendList) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, Objects.requireNonNull(newFriendList, "Friend list is required"), guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given room marked as explored, or this instance
     * unchanged when the room was already visited.
     *
     * @param roomId the room the player has entered; must not be null
     */
    // Identity comparison is intentional: visit returns the same instance (this) when the room was
    // already explored, so reference identity is the no-op sentinel we test for here.
    @SuppressWarnings("ReferenceEquality")
    public Player exploreRoom(RoomId roomId) {
        PlayerExploration updated = exploration.visit(roomId);
        if (updated == exploration) {
            return this;
        }
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, updated, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given unlocked-achievements component.
     *
     * @param newAchievements the new achievements state; must not be null
     */
    public Player withAchievements(PlayerAchievements newAchievements) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, Objects.requireNonNull(newAchievements, "Achievements are required"), exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given reputation standings.
     *
     * @param newReputation the new reputation state; must not be null
     */
    public Player withReputation(PlayerReputation newReputation) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, Objects.requireNonNull(newReputation, "Reputation is required"), achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given tamed-companion collection.
     *
     * @param newPets the new tamed-pet state; must not be null
     */
    public Player withTamedPets(PlayerPets newPets) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, Objects.requireNonNull(newPets, "Pets are required"), reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    @Override
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

    /**
     * Returns the player's transient ridden-mount state.
     *
     * <p>This state is in-memory only and is never persisted to JSON, so a reconnecting player is
     * always {@linkplain PlayerMount#dismounted() dismounted} even though the mount item itself
     * remains in their inventory.
     */
    @JsonIgnore
    public PlayerMount mount() {
        return combatState.mount();
    }

    /**
     * Returns {@code true} while the player is currently riding a mount. In-memory only, never
     * persisted.
     */
    @JsonIgnore
    public boolean isMounted() {
        return combatState.mount().isMounted();
    }

    /**
     * Returns a copy of this player with the given ridden-mount state.
     *
     * <p>The mount state is in-memory only and is never persisted.
     *
     * @param newMount the new mount state; must not be null (use {@link PlayerMount#dismounted()} to
     *                 dismount)
     */
    public Player withMount(PlayerMount newMount) {
        return new Player(identity, combatState.withMount(newMount), preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
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
        return new Player(identity, combatState.die(), preferences, abilities, inventory, equipment, false, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player respawn() {
        return new Player(identity, combatState.respawn(), preferences, abilities, inventory, equipment, false, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player withoutEffects() {
        return new Player(identity, combatState.withoutEffects(), preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player withAnsiEnabled(boolean enabled) {
        return new Player(identity, combatState, preferences.withAnsiEnabled(enabled), abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with autoloot enabled or disabled, preserving all other state.
     * When enabled, items dropped by mobs the player kills solo are placed directly into the player's
     * inventory instead of onto the room floor (subject to carry capacity).
     *
     * @param enabled whether autoloot should be enabled
     * @return an updated player
     */
    public Player withAutoLootEnabled(boolean enabled) {
        return new Player(identity, combatState, preferences.withAutoLootEnabled(enabled), abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with brief mode enabled or disabled, preserving all other state.
     * When enabled, movement (walking between rooms) omits the room's prose description line and shows
     * only the room name, exits, items, and occupants; explicit {@code LOOK} always shows the full text.
     *
     * @param enabled whether brief mode should be enabled
     * @return an updated player
     */
    public Player withBriefModeEnabled(boolean enabled) {
        return new Player(identity, combatState, preferences.withBriefModeEnabled(enabled), abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with auto-assist enabled or disabled, preserving all other state.
     * When enabled, this player is automatically joined into a party-mate's fight against a fresh mob
     * (exactly as if they had typed {@code ASSIST <attacker>}) whenever the party-mate lands the opening
     * attack on a mob that was not already fighting anyone, provided this player is present, not already
     * in combat, not resting, and not dead.
     *
     * @param enabled whether auto-assist should be enabled
     * @return an updated player
     */
    public Player withAutoAssistEnabled(boolean enabled) {
        return new Player(identity, combatState, preferences.withAutoAssistEnabled(enabled), abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given prompt format string, preserving all other state.
     *
     * @param nextFormat the new prompt format (token substitution handled by {@code PromptRenderer})
     * @return an updated player
     */
    public Player withPromptFormat(String nextFormat) {
        return new Player(identity, combatState, preferences.withPromptFormat(nextFormat), abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player withVitals(PlayerVitals updatedVitals) {
        return new Player(identity, combatState.withVitals(updatedVitals), preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player withDead(boolean dead) {
        return new Player(identity, combatState.withDead(dead), preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player withLearnedAbilities(List<AbilityId> learnedAbilities) {
        return new Player(identity, combatState, preferences, abilities.withLearned(learnedAbilities), inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given titles replacing the current set.
     *
     * @param earnedTitles the new titles list
     */
    public Player withTitles(List<String> earnedTitles) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles.withEarned(earnedTitles), aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given title granted, unless it was
     * already earned, in which case this instance is returned unchanged.
     *
     * @param title the title to grant; must not be null
     */
    public Player grantTitle(String title) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles.grant(title), aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given earned title selected as the active
     * (displayed) title.
     *
     * @param title the title to activate; must be an earned title (matched case-insensitively)
     * @throws IllegalArgumentException when the title has not been earned
     */
    public Player withActiveTitle(String title) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles.withActive(title), aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with no active (displayed) title, or this instance
     * unchanged when none was active.
     */
    // Identity comparison is intentional: clearActive returns the same instance (this) when no title
    // was active, so reference identity is the no-op sentinel we test for here.
    @SuppressWarnings("ReferenceEquality")
    public Player clearActiveTitle() {
        PlayerTitles cleared = titles.clearActive();
        if (cleared == titles) {
            return this;
        }
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, cleared, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given alias defined or overwritten.
     *
     * @param name      the alias name; case-insensitive, must not be blank
     * @param expansion the command line the alias expands to; must not be blank
     */
    public Player defineAlias(String name, String expansion) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases.define(name, expansion), mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given alias removed, unchanged if it did
     * not exist.
     *
     * @param name the alias name to remove; case-insensitive
     */
    public Player removeAlias(String name) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases.remove(name), mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given mailbox replacing the current one.
     *
     * @param newMailbox the new mailbox state
     */
    public Player withMailbox(PlayerMailbox newMailbox) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, newMailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given sustenance (hunger/thirst) state.
     *
     * @param newSustenance the new sustenance state; must not be null
     */
    public Player withSustenance(PlayerSustenance newSustenance) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, Objects.requireNonNull(newSustenance, "Sustenance is required"), pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player withInventory(List<Item> items) {
        return new Player(identity, combatState, preferences, abilities, inventory.withItems(items), equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player addItem(Item item) {
        return new Player(identity, combatState, preferences, abilities, inventory.addItem(item), equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player removeItem(Item item) {
        return new Player(identity, combatState, preferences, abilities, inventory.removeItem(item), equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    public Player withEquipment(PlayerEquipment nextEquipment) {
        return new Player(identity, combatState, preferences, abilities, inventory, nextEquipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given identity (level and experience).
     */
    public Player withIdentity(PlayerIdentity nextIdentity) {
        return new Player(nextIdentity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns this player's custom LOOK description, or an empty string when none is set.
     */
    @JsonIgnore
    public String description() {
        return identity.description();
    }

    /**
     * Returns a copy of this player with the given custom LOOK description; pass a {@code null} or
     * blank string to clear it back to the default generated line. The text is trimmed.
     *
     * @param newDescription the new description, or {@code null}/blank to clear
     */
    public Player withDescription(String newDescription) {
        return withIdentity(identity.withDescription(newDescription));
    }

    /**
     * Returns the username of this player's spouse (see the MARRY command), or {@code null} when the
     * player is unmarried.
     */
    @JsonIgnore
    public String spouse() {
        return identity.spouse();
    }

    /**
     * Returns {@code true} when this player is currently married.
     */
    @JsonIgnore
    public boolean isMarried() {
        return identity.spouse() != null;
    }

    /**
     * Returns a copy of this player bonded to the given spouse, or unmarried when {@code newSpouse}
     * is {@code null}/blank.
     *
     * @param newSpouse the spouse's username display value, or {@code null}/blank to end the bond
     */
    public Player withSpouse(String newSpouse) {
        return withIdentity(identity.withSpouse(newSpouse));
    }

    /**
     * Returns the id (value form) of the room this player has bound their recall/respawn point to via
     * the BIND command, or {@code null} when they have never bound.
     */
    @JsonIgnore
    public String boundRoomId() {
        return identity.boundRoomId();
    }

    /**
     * Returns a copy of this player anchored to the given recall/respawn room, or unbound when
     * {@code newBoundRoomId} is {@code null}/blank (recall/respawn then default to the starting room).
     *
     * @param newBoundRoomId the bound room id value, or {@code null}/blank to clear the anchor
     */
    public Player withBoundRoomId(String newBoundRoomId) {
        return withIdentity(identity.withBoundRoomId(newBoundRoomId));
    }

    /**
     * Returns a copy of this player with the resting flag set to the given value.
     *
     * <p>The resting state is in-memory only and is never persisted.
     *
     * @param resting {@code true} to enter a resting state, {@code false} to wake up
     */
    public Player withResting(boolean resting) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the stealth (hidden) flag set to the given value.
     *
     * <p>The stealth state is in-memory only and is never persisted.
     *
     * @param active {@code true} to enter stealth, {@code false} to leave it
     */
    public Player withStealth(boolean active) {
        return new Player(identity, combatState.withStealth(active), preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given gold balance.
     *
     * @param newGold the new gold amount; negative values are clamped to 0
     */
    public Player withGold(int newGold) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, newGold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
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
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, newBankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
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
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, quest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given active daily quest set. The daily-quest slot is
     * independent of the story-quest slot ({@link #withActiveQuest(ActiveQuest)}).
     *
     * @param quest the daily quest to set, or {@code null} to clear the active daily quest
     */
    public Player withActiveDailyQuest(ActiveQuest quest) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, quest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the total kill count set to the given value.
     *
     * @param newTotalKills the new kill count; negative values are clamped to 0
     */
    public Player withTotalKills(long newTotalKills) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, newTotalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given practice point balance.
     *
     * @param newPracticePoints the new practice point count; negative values are clamped to 0
     */
    public Player withPracticePoints(int newPracticePoints) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, newPracticePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, auctionWatchList);
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
            getFriends(),
            getGuildId(),
            vault.items(),
            titles.active(),
            completedQuests.toIdList(),
            duelWins,
            duelLosses,
            proficiencies.toStringMap(),
            preferences.autoLootEnabled(),
            identity.description().isEmpty() ? null : identity.description(),
            pets.customNames(),
            preferences.briefModeEnabled(),
            activeDailyQuest,
            pets.customDescriptions(),
            vault.capacityTier(),
            identity.spouse(),
            identity.boundRoomId(),
            preferences.autoAssistEnabled(),
            getAuctionWatches()
        );
    }

    /**
     * Returns the number of duels this player has won by combat resolution, for JSON serialisation.
     */
    @JsonProperty("duelWins")
    public int getDuelWins() {
        return duelWins;
    }

    /**
     * Returns the number of duels this player has lost by combat resolution, for JSON serialisation.
     */
    @JsonProperty("duelLosses")
    public int getDuelLosses() {
        return duelLosses;
    }

    /**
     * Returns a copy of this player with the given duel-win count.
     *
     * @param newDuelWins the new duel-win count; negative values are clamped to 0
     */
    public Player withDuelWins(int newDuelWins) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, newDuelWins, duelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns a copy of this player with the given duel-loss count.
     *
     * @param newDuelLosses the new duel-loss count; negative values are clamped to 0
     */
    public Player withDuelLosses(int newDuelLosses) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, newDuelLosses, proficiencies, auctionWatchList);
    }

    /**
     * Returns this player's crafting proficiencies as a profession-id to accumulated-points map, for
     * JSON serialisation. Empty when the player has never practised a profession.
     */
    @JsonProperty("proficiencies")
    public Map<String, Integer> getProficiencies() {
        return proficiencies.toStringMap();
    }

    /**
     * Returns this player's crafting proficiency component (accumulated points per profession).
     */
    @JsonIgnore
    public PlayerProficiencies proficiencies() {
        return proficiencies;
    }

    /**
     * Returns a copy of this player with the given crafting proficiencies.
     *
     * @param newProficiencies the new proficiency state; must not be null
     */
    public Player withProficiencies(PlayerProficiencies newProficiencies) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, Objects.requireNonNull(newProficiencies, "Proficiencies are required"), auctionWatchList);
    }

    /**
     * Returns the player's Auction House watch keywords (see {@code AUCTION WATCH}) for JSON
     * serialisation, in insertion order. Empty for players who watch nothing; the field is written
     * even when empty (it costs nothing and keeps saves self-describing).
     */
    @JsonProperty("auctionWatches")
    public List<String> getAuctionWatches() {
        return List.copyOf(auctionWatchList.keywords());
    }

    /**
     * Returns this player's Auction House watch list (keywords watched via {@code AUCTION WATCH}).
     */
    @JsonIgnore
    public PlayerAuctionWatchList auctionWatchList() {
        return auctionWatchList;
    }

    /**
     * Returns a copy of this player with the given Auction House watch list.
     *
     * @param newAuctionWatchList the new watch-list state; must not be null
     */
    public Player withAuctionWatchList(PlayerAuctionWatchList newAuctionWatchList) {
        return new Player(identity, combatState, preferences, abilities, inventory, equipment, resting, gold, activeQuest, activeDailyQuest, totalKills, practicePoints, bankedGold, titles, aliases, mailbox, sustenance, pets, reputation, achievements, exploration, ignoreList, friendList, guildMembership, vault, completedQuests, duelWins, duelLosses, proficiencies, Objects.requireNonNull(newAuctionWatchList, "Auction watch list is required"));
    }
}
