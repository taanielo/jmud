package io.taanielo.jmud.core.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.repository.AbilityRepository;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.craft.Recipe;
import io.taanielo.jmud.core.craft.RecipeRepository;
import io.taanielo.jmud.core.creation.NewbieKit;
import io.taanielo.jmud.core.creation.NewbieKitRepository;
import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionRepository;
import io.taanielo.jmud.core.gathering.ResourceNode;
import io.taanielo.jmud.core.gathering.ResourceNodeRepository;
import io.taanielo.jmud.core.mob.LootEntry;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.MobTemplateRepository;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.salvage.SalvageMaterial;
import io.taanielo.jmud.core.salvage.SalvageTier;
import io.taanielo.jmud.core.salvage.SalvageTierRepository;
import io.taanielo.jmud.core.shop.Shop;
import io.taanielo.jmud.core.shop.ShopRepository;
import io.taanielo.jmud.core.shop.StockEntry;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.ItemSet;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.repository.ItemCatalog;
import io.taanielo.jmud.core.world.repository.ItemSetRepository;
import io.taanielo.jmud.core.world.repository.RoomCatalog;

/**
 * Whole-world content-completeness check (issue #530), generalising the area-consistency pattern of
 * issue #529 to the machine-checkable rules in {@code docs/content-dod.md}.
 *
 * <p>It verifies the cross-reference invariants that keep shipped game data complete:
 *
 * <ul>
 *   <li><b>Mob</b> — an explicit {@code xp_reward} (enforced at load, surfaced here), a spawn room
 *       that exists, loot item ids that resolve, and an attack id (when present) that resolves;</li>
 *   <li><b>Ability</b> — every skill/spell is reachable by at least one class (starting kit or
 *       trainable pool) — no orphan abilities;</li>
 *   <li><b>Item</b> — every item is obtainable somewhere (shop stock, mob loot, quest reward, craft
 *       recipe output, gathering yield, salvage material, newbie kit, or placement in a room) — no
 *       orphan items;</li>
 *   <li><b>Quest</b> — giver/target/receiver mobs, rooms, reward items, reward factions and
 *       prerequisites all reference existing content.</li>
 * </ul>
 *
 * <p>The checker performs no mutation and no networking, so it is unit-testable without a running
 * server (AGENTS.md §10). It returns a list of human-readable problem strings; an empty list means
 * the shipped content is complete.
 */
public class ContentCompletenessChecker {

    private static final String DOD = "docs/content-dod.md";

    private final MobTemplateRepository mobTemplateRepository;
    private final RoomCatalog roomCatalog;
    private final ItemCatalog itemCatalog;
    private final AttackRepository attackRepository;
    private final AbilityRepository abilityRepository;
    private final ClassRepository classRepository;
    private final ShopRepository shopRepository;
    private final QuestRepository questRepository;
    private final List<RecipeRepository> recipeRepositories;
    private final ResourceNodeRepository resourceNodeRepository;
    private final NewbieKitRepository newbieKitRepository;
    private final SalvageTierRepository salvageTierRepository;
    private final FactionRepository factionRepository;
    private final ItemSetRepository itemSetRepository;

    /**
     * Creates a checker over every data source that a completeness rule needs.
     *
     * @param mobTemplateRepository  source of mob templates (xp/spawn/loot/attack rules)
     * @param roomCatalog            bulk access to every room (spawn/quest-room resolution)
     * @param itemCatalog            bulk access to every item (orphan-item enumeration)
     * @param attackRepository       source of attack definitions (mob attack resolution)
     * @param abilityRepository      source of abilities (orphan-ability enumeration)
     * @param classRepository        source of classes (ability reachability)
     * @param shopRepository         source of shop stock (an item-distribution channel)
     * @param questRepository        source of quests (reference-integrity rule)
     * @param recipeRepositories     every crafting-recipe repository (output items are obtainable)
     * @param resourceNodeRepository source of gathering yields (an item-distribution channel)
     * @param newbieKitRepository    source of the starting kit (an item-distribution channel)
     * @param salvageTierRepository  source of salvage yields (an item-distribution channel)
     * @param factionRepository      source of factions (quest reputation-reward resolution)
     * @param itemSetRepository      source of item sets (piece reference-resolution + obtainability)
     */
    public ContentCompletenessChecker(
        MobTemplateRepository mobTemplateRepository,
        RoomCatalog roomCatalog,
        ItemCatalog itemCatalog,
        AttackRepository attackRepository,
        AbilityRepository abilityRepository,
        ClassRepository classRepository,
        ShopRepository shopRepository,
        QuestRepository questRepository,
        List<RecipeRepository> recipeRepositories,
        ResourceNodeRepository resourceNodeRepository,
        NewbieKitRepository newbieKitRepository,
        SalvageTierRepository salvageTierRepository,
        FactionRepository factionRepository,
        ItemSetRepository itemSetRepository
    ) {
        this.mobTemplateRepository = Objects.requireNonNull(mobTemplateRepository, "Mob repository is required");
        this.roomCatalog = Objects.requireNonNull(roomCatalog, "Room catalog is required");
        this.itemCatalog = Objects.requireNonNull(itemCatalog, "Item catalog is required");
        this.attackRepository = Objects.requireNonNull(attackRepository, "Attack repository is required");
        this.abilityRepository = Objects.requireNonNull(abilityRepository, "Ability repository is required");
        this.classRepository = Objects.requireNonNull(classRepository, "Class repository is required");
        this.shopRepository = Objects.requireNonNull(shopRepository, "Shop repository is required");
        this.questRepository = Objects.requireNonNull(questRepository, "Quest repository is required");
        this.recipeRepositories =
            List.copyOf(Objects.requireNonNull(recipeRepositories, "Recipe repositories are required"));
        this.resourceNodeRepository =
            Objects.requireNonNull(resourceNodeRepository, "Resource node repository is required");
        this.newbieKitRepository = Objects.requireNonNull(newbieKitRepository, "Newbie kit repository is required");
        this.salvageTierRepository =
            Objects.requireNonNull(salvageTierRepository, "Salvage tier repository is required");
        this.factionRepository = Objects.requireNonNull(factionRepository, "Faction repository is required");
        this.itemSetRepository = Objects.requireNonNull(itemSetRepository, "Item set repository is required");
    }

    /**
     * Runs every completeness rule and returns the problems found.
     *
     * @return an ordered list of problem descriptions; empty when shipped content is complete
     */
    public List<String> check() {
        List<String> problems = new ArrayList<>();

        // Mobs are loaded once and reused by the mob, item (loot channel) and quest rules. A failure
        // to load (e.g. a mob missing its required xp_reward) is itself an actionable problem.
        List<MobTemplate> mobs = new ArrayList<>();
        try {
            mobs.addAll(mobTemplateRepository.findAll());
        } catch (Exception e) {
            problems.add("Failed to load mob data (" + DOD + " → Mob): " + e.getMessage());
        }

        Set<String> roomIds = loadRoomIds(problems);
        Set<String> itemIds = loadItemIds(problems);
        Set<String> obtainable = collectObtainableItemIds(mobs, problems);

        checkMobs(mobs, roomIds, itemIds, problems);
        checkAbilities(problems);
        checkItems(itemIds, obtainable, problems);
        checkItemSets(itemIds, obtainable, problems);
        checkQuests(mobs, roomIds, itemIds, problems);

        return problems;
    }

    // ── mob rules ────────────────────────────────────────────────────────

    private void checkMobs(List<MobTemplate> mobs, Set<String> roomIds, Set<String> itemIds, List<String> problems) {
        for (MobTemplate mob : mobs) {
            String mobId = mob.id().getValue();
            if (!roomIds.contains(mob.spawnRoomId().getValue())) {
                problems.add("Mob '" + mobId + "' has spawn_room_id '" + mob.spawnRoomId().getValue()
                    + "' which is not a known room (" + DOD + " → Mob)");
            }
            for (LootEntry loot : mob.lootTable()) {
                if (!itemIds.contains(loot.itemId().getValue())) {
                    problems.add("Mob '" + mobId + "' drops unknown loot item '" + loot.itemId().getValue()
                        + "' (" + DOD + " → Mob)");
                }
            }
            AttackId attackId = mob.attackId();
            if (attackId != null && !attackExists(attackId, problems)) {
                problems.add("Mob '" + mobId + "' references unknown attack_id '" + attackId.getValue()
                    + "' (" + DOD + " → Mob)");
            }
        }
    }

    private boolean attackExists(AttackId attackId, List<String> problems) {
        try {
            return attackRepository.findById(attackId).isPresent();
        } catch (Exception e) {
            problems.add("Failed to resolve attack '" + attackId.getValue() + "': " + e.getMessage());
            return true; // treat a load failure as "present" so it is not double-reported as missing
        }
    }

    // ── ability rules ────────────────────────────────────────────────────

    private void checkAbilities(List<String> problems) {
        Set<String> reachable = new HashSet<>();
        try {
            for (ClassDefinition definition : classRepository.findAll()) {
                definition.startingAbilityIds().forEach(id -> reachable.add(id.getValue()));
                definition.trainableAbilityIds().forEach(id -> reachable.add(id.getValue()));
            }
        } catch (Exception e) {
            problems.add("Failed to load class data (" + DOD + " → Ability): " + e.getMessage());
            return;
        }

        List<Ability> abilities;
        try {
            abilities = abilityRepository.findAll();
        } catch (Exception e) {
            problems.add("Failed to load ability data (" + DOD + " → Ability): " + e.getMessage());
            return;
        }

        Set<String> orphans = new TreeSet<>();
        for (Ability ability : abilities) {
            if (!reachable.contains(ability.id().getValue())) {
                orphans.add(ability.id().getValue());
            }
        }
        for (String orphan : orphans) {
            problems.add("Ability '" + orphan
                + "' is not reachable by any class (no starting kit or trainable pool lists it) ("
                + DOD + " → Ability)");
        }
    }

    // ── item rules ───────────────────────────────────────────────────────

    private void checkItems(Set<String> itemIds, Set<String> obtainable, List<String> problems) {
        Set<String> orphans = new TreeSet<>();
        for (String itemId : itemIds) {
            if (!obtainable.contains(itemId)) {
                orphans.add(itemId);
            }
        }
        for (String orphan : orphans) {
            problems.add("Item '" + orphan
                + "' is not obtainable anywhere (no shop, loot, quest reward, recipe, gather, salvage, "
                + "newbie kit or room placement grants it) (" + DOD + " → Item)");
        }
    }

    // ── item-set rules ───────────────────────────────────────────────────

    private void checkItemSets(Set<String> itemIds, Set<String> obtainable, List<String> problems) {
        List<ItemSet> sets;
        try {
            sets = itemSetRepository.findAll();
        } catch (Exception e) {
            problems.add("Failed to load item-set data (" + DOD + " → Item Set): " + e.getMessage());
            return;
        }
        for (ItemSet set : sets) {
            String setId = set.id().getValue();
            for (ItemId pieceId : set.pieceIds()) {
                String piece = pieceId.getValue();
                if (!itemIds.contains(piece)) {
                    problems.add("Item set '" + setId + "' lists piece '" + piece
                        + "' which is not a known item (" + DOD + " → Item Set)");
                } else if (!obtainable.contains(piece)) {
                    problems.add("Item set '" + setId + "' piece '" + piece
                        + "' is not obtainable anywhere (no shop, loot, quest reward, recipe, gather, "
                        + "salvage, newbie kit or room placement grants it) (" + DOD + " → Item Set)");
                }
            }
        }
    }

    private Set<String> collectObtainableItemIds(List<MobTemplate> mobs, List<String> problems) {
        Set<String> obtainable = new HashSet<>();
        try {
            for (Shop shop : shopRepository.findAll()) {
                for (StockEntry entry : shop.stock()) {
                    obtainable.add(entry.itemId().getValue());
                }
            }
        } catch (Exception e) {
            problems.add("Failed to load shop data (" + DOD + " → Item): " + e.getMessage());
        }
        for (MobTemplate mob : mobs) {
            for (LootEntry loot : mob.lootTable()) {
                obtainable.add(loot.itemId().getValue());
            }
        }
        try {
            for (QuestTemplate quest : questRepository.findAll()) {
                addIfPresent(obtainable, quest.itemReward());
                addIfPresent(obtainable, quest.dropItemId());
                addIfPresent(obtainable, quest.packageItemId());
            }
        } catch (Exception e) {
            problems.add("Failed to load quest data (" + DOD + " → Item): " + e.getMessage());
        }
        for (RecipeRepository recipeRepository : recipeRepositories) {
            try {
                for (Recipe recipe : recipeRepository.findAll()) {
                    obtainable.add(recipe.outputItemId().getValue());
                }
            } catch (Exception e) {
                problems.add("Failed to load recipe data (" + DOD + " → Item): " + e.getMessage());
            }
        }
        try {
            for (ResourceNode node : resourceNodeRepository.findAll()) {
                obtainable.add(node.yieldItemId().getValue());
            }
        } catch (Exception e) {
            problems.add("Failed to load resource-node data (" + DOD + " → Item): " + e.getMessage());
        }
        try {
            for (SalvageTier tier : salvageTierRepository.findAll()) {
                for (SalvageMaterial material : tier.materials()) {
                    obtainable.add(material.itemId().getValue());
                }
            }
        } catch (Exception e) {
            problems.add("Failed to load salvage data (" + DOD + " → Item): " + e.getMessage());
        }
        try {
            NewbieKit kit = newbieKitRepository.load();
            kit.itemIds().forEach(id -> obtainable.add(id.getValue()));
        } catch (Exception e) {
            problems.add("Failed to load newbie-kit data (" + DOD + " → Item): " + e.getMessage());
        }
        try {
            for (Room room : roomCatalog.findAll()) {
                for (Item item : room.getItems()) {
                    collectItemAndContents(item, obtainable);
                }
            }
        } catch (Exception e) {
            problems.add("Failed to load room data (" + DOD + " → Item): " + e.getMessage());
        }
        return obtainable;
    }

    private void collectItemAndContents(Item item, Set<String> obtainable) {
        obtainable.add(item.getId().getValue());
        for (Item contained : item.getContainedItems()) {
            collectItemAndContents(contained, obtainable);
        }
    }

    // ── quest rules ──────────────────────────────────────────────────────

    private void checkQuests(List<MobTemplate> mobs, Set<String> roomIds, Set<String> itemIds, List<String> problems) {
        Set<String> mobIds = new HashSet<>();
        for (MobTemplate mob : mobs) {
            mobIds.add(mob.id().getValue());
        }
        Set<String> factionIds = loadFactionIds(problems);

        List<QuestTemplate> quests;
        try {
            quests = questRepository.findAll();
        } catch (Exception e) {
            problems.add("Failed to load quest data (" + DOD + " → Quest): " + e.getMessage());
            return;
        }

        Set<String> questIds = new HashSet<>();
        for (QuestTemplate quest : quests) {
            questIds.add(quest.id().value());
        }

        for (QuestTemplate quest : quests) {
            String qid = quest.id().value();
            checkMobRef(qid, "target_mob_id", quest.targetMobId(), mobIds, problems);
            checkMobRef(qid, "giver_npc_id", quest.giverNpcId(), mobIds, problems);
            checkMobRef(qid, "receiver_npc_id", quest.receiverNpcId(), mobIds, problems);
            checkRoomRef(qid, "receiver_room_id", quest.receiverRoomId(), roomIds, problems);
            for (String roomId : quest.requiredRoomIds()) {
                checkRoomRef(qid, "required_room_ids", roomId, roomIds, problems);
            }
            checkItemRef(qid, "drop_item_id", quest.dropItemId(), itemIds, problems);
            checkItemRef(qid, "package_item_id", quest.packageItemId(), itemIds, problems);
            checkItemRef(qid, "item_reward", quest.itemReward(), itemIds, problems);
            String factionId = quest.reputationRewardFactionId();
            if (factionId != null && !factionIds.contains(factionId)) {
                problems.add("Quest '" + qid + "' reputation_reward_faction_id '" + factionId
                    + "' is not a known faction (" + DOD + " → Quest)");
            }
            String prerequisite = quest.prerequisiteQuestId();
            if (prerequisite != null && !questIds.contains(prerequisite)) {
                problems.add("Quest '" + qid + "' prerequisite_quest_id '" + prerequisite
                    + "' is not a known quest (" + DOD + " → Quest)");
            }
        }
    }

    private void checkMobRef(String qid, String field, @Nullable String mobId, Set<String> mobIds,
        List<String> problems) {
        if (mobId != null && !mobIds.contains(mobId)) {
            problems.add("Quest '" + qid + "' " + field + " '" + mobId + "' is not a known mob ("
                + DOD + " → Quest)");
        }
    }

    private void checkRoomRef(String qid, String field, @Nullable String roomId, Set<String> roomIds,
        List<String> problems) {
        if (roomId != null && !roomIds.contains(roomId)) {
            problems.add("Quest '" + qid + "' " + field + " '" + roomId + "' is not a known room ("
                + DOD + " → Quest)");
        }
    }

    private void checkItemRef(String qid, String field, @Nullable String itemId, Set<String> itemIds,
        List<String> problems) {
        if (itemId != null && !itemIds.contains(itemId)) {
            problems.add("Quest '" + qid + "' " + field + " '" + itemId + "' is not a known item ("
                + DOD + " → Quest)");
        }
    }

    // ── shared loaders ───────────────────────────────────────────────────

    private Set<String> loadRoomIds(List<String> problems) {
        Set<String> roomIds = new HashSet<>();
        try {
            for (Room room : roomCatalog.findAll()) {
                roomIds.add(room.getId().getValue());
            }
        } catch (Exception e) {
            problems.add("Failed to load room data: " + e.getMessage());
        }
        return roomIds;
    }

    private Set<String> loadItemIds(List<String> problems) {
        Set<String> itemIds = new HashSet<>();
        try {
            for (Item item : itemCatalog.findAll()) {
                itemIds.add(item.getId().getValue());
            }
        } catch (Exception e) {
            problems.add("Failed to load item data: " + e.getMessage());
        }
        return itemIds;
    }

    private Set<String> loadFactionIds(List<String> problems) {
        Set<String> factionIds = new HashSet<>();
        try {
            for (Faction faction : factionRepository.findAll()) {
                factionIds.add(faction.id().value());
            }
        } catch (Exception e) {
            problems.add("Failed to load faction data (" + DOD + " → Quest): " + e.getMessage());
        }
        return factionIds;
    }

    private static void addIfPresent(Set<String> target, @Nullable String value) {
        if (value != null) {
            target.add(value);
        }
    }
}
