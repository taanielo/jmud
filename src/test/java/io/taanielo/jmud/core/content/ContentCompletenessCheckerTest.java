package io.taanielo.jmud.core.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCooldown;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityDefinition;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;
import io.taanielo.jmud.core.ability.repository.AbilityRepository;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.craft.Recipe;
import io.taanielo.jmud.core.craft.RecipeRepository;
import io.taanielo.jmud.core.creation.NewbieKit;
import io.taanielo.jmud.core.creation.NewbieKitRepository;
import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.FactionRepository;
import io.taanielo.jmud.core.gathering.ResourceNode;
import io.taanielo.jmud.core.gathering.ResourceNodeRepository;
import io.taanielo.jmud.core.mob.LootEntry;
import io.taanielo.jmud.core.mob.MobId;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.MobTemplateRepository;
import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.salvage.SalvageTier;
import io.taanielo.jmud.core.salvage.SalvageTierRepository;
import io.taanielo.jmud.core.shop.Shop;
import io.taanielo.jmud.core.shop.ShopId;
import io.taanielo.jmud.core.shop.ShopRepository;
import io.taanielo.jmud.core.shop.StockEntry;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.ItemCatalog;
import io.taanielo.jmud.core.world.repository.RoomCatalog;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;
import io.taanielo.jmud.core.world.repository.json.JsonRoomRepository;

class ContentCompletenessCheckerTest {

    private static final Path DATA_ROOT = Path.of("data");

    @Test
    void shippedWorldContentIsComplete() throws Exception {
        JsonItemRepository items = new JsonItemRepository(DATA_ROOT);
        JsonRoomRepository rooms = new JsonRoomRepository(items, DATA_ROOT);
        ContentCompletenessChecker checker = new ContentCompletenessChecker(
            new io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository(DATA_ROOT),
            rooms,
            items,
            new io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository(DATA_ROOT),
            new io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository(DATA_ROOT),
            new io.taanielo.jmud.core.character.repository.json.JsonClassRepository(DATA_ROOT),
            new io.taanielo.jmud.core.shop.repository.json.JsonShopRepository(DATA_ROOT),
            new io.taanielo.jmud.core.quest.repository.json.JsonQuestRepository(DATA_ROOT),
            List.<RecipeRepository>of(
                new io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository(DATA_ROOT),
                new io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository(DATA_ROOT, "recipes/alchemy"),
                new io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository(DATA_ROOT, "recipes/cooking")),
            new io.taanielo.jmud.core.gathering.repository.json.JsonResourceNodeRepository(DATA_ROOT),
            new io.taanielo.jmud.core.creation.json.JsonNewbieKitRepository(DATA_ROOT),
            new io.taanielo.jmud.core.salvage.repository.json.JsonSalvageTierRepository(DATA_ROOT),
            new io.taanielo.jmud.core.faction.repository.json.JsonFactionRepository(DATA_ROOT));

        List<String> problems = checker.check();

        assertTrue(problems.isEmpty(), "shipped content must be complete but had: " + problems);
    }

    @Test
    void baselineFixtureIsComplete() {
        assertTrue(check(baseline()).isEmpty(), "baseline fixture should be complete");
    }

    @Test
    void reportsOrphanAbility() {
        Fixture f = baseline();
        f.abilities.add(ability("spell.orphan"));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("spell.orphan") && p.contains("not reachable")),
            "expected an orphan-ability problem, got: " + problems);
    }

    @Test
    void reportsUnobtainableItem() {
        Fixture f = baseline();
        f.items.add(item("gem"));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("gem") && p.contains("not obtainable")),
            "expected an unobtainable-item problem, got: " + problems);
    }

    @Test
    void reportsQuestWithDanglingMobReference() {
        Fixture f = baseline();
        f.quests.add(new QuestTemplate(QuestId.of("q-ghost"), "Ghost Hunt", "kill the ghost",
            "ghost-mob", 1, 10, 5));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("ghost-mob") && p.contains("not a known mob")),
            "expected a dangling quest reference problem, got: " + problems);
    }

    @Test
    void reportsMobWithUnknownSpawnRoom() {
        Fixture f = baseline();
        f.mobs.add(mob("stray", "nowhere", List.of()));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("nowhere") && p.contains("not a known room")),
            "expected an unknown-spawn-room problem, got: " + problems);
    }

    @Test
    void reportsMobWithUnknownLootItem() {
        Fixture f = baseline();
        f.mobs.add(mob("hoarder", "r1", List.of(new LootEntry(ItemId.of("phantom"), 1.0))));

        List<String> problems = check(f);

        assertTrue(problems.stream().anyMatch(p -> p.contains("phantom") && p.contains("unknown loot")),
            "expected an unknown-loot problem, got: " + problems);
    }

    // ── fixture & fakes ──────────────────────────────────────────────────

    private static List<String> check(Fixture f) {
        return new ContentCompletenessChecker(
            new FakeMobRepository(f.mobs),
            new FakeRoomCatalog(f.rooms),
            new FakeItemCatalog(f.items),
            new FakeAttackRepository(),
            new FakeAbilityRepository(f.abilities),
            new FakeClassRepository(f.classes),
            new FakeShopRepository(f.shops),
            new FakeQuestRepository(f.quests),
            List.<RecipeRepository>of(new FakeRecipeRepository(f.recipes)),
            new FakeResourceNodeRepository(f.nodes),
            new FakeNewbieKitRepository(f.newbieKit),
            new FakeSalvageTierRepository(f.salvageTiers),
            new FakeFactionRepository(f.factions)).check();
    }

    /**
     * A minimal internally-consistent world: one room, one item obtainable from a shop, one mob, one
     * ability reachable via one class, one faction. Every rule passes; each test perturbs one thing.
     */
    private static Fixture baseline() {
        Fixture f = new Fixture();
        f.rooms.add(new Room(RoomId.of("r1"), "Room r1", "desc", Map.of(), List.of(), List.of()));
        f.items.add(item("sword"));
        f.shops.add(new Shop(ShopId.of("shop-1"), "Shop", RoomId.of("r1"),
            List.of(new StockEntry(ItemId.of("sword"), null)), 0.5));
        f.mobs.add(mob("rat", "r1", List.of()));
        f.abilities.add(ability("skill.bash"));
        f.classes.add(new ClassDefinition(ClassId.of("warrior"), "Warrior", 0, 0, 0,
            List.of(AbilityId.of("skill.bash")), List.of()));
        f.factions.add(new Faction(FactionId.of("militia"), "Militia", "town guard", 5, -50, 0.0));
        f.newbieKit = new NewbieKit(0, List.of());
        return f;
    }

    private static Item item(String id) {
        return Item.builder(ItemId.of(id), id, "An item.", ItemAttributes.empty()).weight(1).value(10).build();
    }

    private static MobTemplate mob(String id, String spawnRoomId, List<LootEntry> loot) {
        return new MobTemplate(MobId.of(id), id, 10, null, null, false, loot,
            RoomId.of(spawnRoomId), 1, 10, 5, null, List.of(), false);
    }

    private static Ability ability(String id) {
        return new AbilityDefinition(AbilityId.of(id), id, AbilityType.SKILL, 1,
            new AbilityCost(0, 0), new AbilityCooldown(0), AbilityTargeting.NONE,
            List.of(), List.of(), List.of());
    }

    private static final class Fixture {
        final List<MobTemplate> mobs = new ArrayList<>();
        final List<Room> rooms = new ArrayList<>();
        final List<Item> items = new ArrayList<>();
        final List<Ability> abilities = new ArrayList<>();
        final List<ClassDefinition> classes = new ArrayList<>();
        final List<Shop> shops = new ArrayList<>();
        final List<QuestTemplate> quests = new ArrayList<>();
        final List<Recipe> recipes = new ArrayList<>();
        final List<ResourceNode> nodes = new ArrayList<>();
        final List<SalvageTier> salvageTiers = new ArrayList<>();
        final List<Faction> factions = new ArrayList<>();
        NewbieKit newbieKit = new NewbieKit(0, List.of());
    }

    private record FakeMobRepository(List<MobTemplate> mobs) implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() {
            return List.copyOf(mobs);
        }
    }

    private record FakeRoomCatalog(List<Room> rooms) implements RoomCatalog {
        @Override
        public List<Room> findAll() {
            return List.copyOf(rooms);
        }
    }

    private record FakeItemCatalog(List<Item> items) implements ItemCatalog {
        @Override
        public List<Item> findAll() {
            return List.copyOf(items);
        }
    }

    private record FakeAttackRepository() implements AttackRepository {
        @Override
        public Optional<AttackDefinition> findById(AttackId id) {
            return Optional.empty();
        }
    }

    private record FakeAbilityRepository(List<Ability> abilities) implements AbilityRepository {
        @Override
        public Optional<Ability> findById(AbilityId id) {
            return abilities.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public List<Ability> findAll() {
            return List.copyOf(abilities);
        }
    }

    private record FakeClassRepository(List<ClassDefinition> classes) implements ClassRepository {
        @Override
        public Optional<ClassDefinition> findById(ClassId id) {
            return classes.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<ClassDefinition> findAll() {
            return List.copyOf(classes);
        }
    }

    private record FakeShopRepository(List<Shop> shops) implements ShopRepository {
        @Override
        public List<Shop> findAll() {
            return List.copyOf(shops);
        }

        @Override
        public Optional<Shop> findByRoomId(RoomId roomId) {
            return shops.stream().filter(s -> s.roomId().equals(roomId)).findFirst();
        }
    }

    private record FakeQuestRepository(List<QuestTemplate> quests) implements QuestRepository {
        @Override
        public List<QuestTemplate> findAll() {
            return List.copyOf(quests);
        }

        @Override
        public Optional<QuestTemplate> findById(QuestId id) {
            return quests.stream().filter(q -> q.id().equals(id)).findFirst();
        }
    }

    private record FakeRecipeRepository(List<Recipe> recipes) implements RecipeRepository {
        @Override
        public List<Recipe> findAll() {
            return List.copyOf(recipes);
        }
    }

    private record FakeResourceNodeRepository(List<ResourceNode> nodes) implements ResourceNodeRepository {
        @Override
        public List<ResourceNode> findAll() {
            return List.copyOf(nodes);
        }
    }

    private record FakeNewbieKitRepository(NewbieKit kit) implements NewbieKitRepository {
        @Override
        public NewbieKit load() {
            return kit;
        }
    }

    private record FakeSalvageTierRepository(List<SalvageTier> tiers) implements SalvageTierRepository {
        @Override
        public List<SalvageTier> findAll() {
            return List.copyOf(tiers);
        }
    }

    private record FakeFactionRepository(List<Faction> factions) implements FactionRepository {
        @Override
        public List<Faction> findAll() {
            return List.copyOf(factions);
        }

        @Override
        public Optional<Faction> findById(FactionId id) {
            return factions.stream().filter(fc -> fc.id().equals(id)).findFirst();
        }
    }
}
