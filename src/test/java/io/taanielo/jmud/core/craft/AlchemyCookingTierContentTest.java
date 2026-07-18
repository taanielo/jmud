package io.taanielo.jmud.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemEffect;
import io.taanielo.jmud.core.world.ItemEffectOperation;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Content smoke-test for the Alchemy ({@code BREW}) and Cooking ({@code COOK}) {@code min_skill} 3
 * and 4 tiers added in issue #695. It confirms the new recipes, output items and cooking buff
 * effects ship correctly and are genuine upgrades over the professions' existing tier-1/2 content,
 * closing the parity gap where those two professions capped at {@code min_skill} 2.
 */
class AlchemyCookingTierContentTest {

    private int statBonus(EffectDefinition definition) {
        int total = 0;
        for (EffectModifier modifier : definition.modifiers()) {
            total += modifier.amount();
        }
        return total;
    }

    @Test
    void alchemyRecipes_addSkill3AndSkill4Tiers() throws RecipeRepositoryException {
        JsonRecipeRepository alchemyRepo = new JsonRecipeRepository(Path.of("data"), "recipes/alchemy");
        List<Recipe> recipes = alchemyRepo.findAll();

        assertTrue(recipes.stream().anyMatch(r -> r.minSkill() == 3),
            "Alchemy must offer a min_skill 3 recipe");
        assertTrue(recipes.stream().anyMatch(r -> r.minSkill() == 4),
            "Alchemy must offer a min_skill 4 recipe");
    }

    @Test
    void cookingRecipes_addSkill3AndSkill4Tiers() throws RecipeRepositoryException {
        JsonRecipeRepository cookingRepo = new JsonRecipeRepository(Path.of("data"), "recipes/cooking");
        List<Recipe> recipes = cookingRepo.findAll();

        assertTrue(recipes.stream().anyMatch(r -> r.minSkill() == 3),
            "Cooking must offer a min_skill 3 recipe");
        assertTrue(recipes.stream().anyMatch(r -> r.minSkill() == 4),
            "Cooking must offer a min_skill 4 recipe");
    }

    @Test
    void greaterVigorElixir_restoresMoreThanElixirOfVigor() throws RepositoryException {
        JsonItemRepository items = new JsonItemRepository(Path.of("data"));
        Item vigor = items.findById(ItemId.of("elixir-of-vigor")).orElseThrow();
        Item greater = items.findById(ItemId.of("greater-vigor-elixir")).orElseThrow();

        int vigorHp = vigor.getAttributes().getStats().getOrDefault("hp", 0);
        int greaterHp = greater.getAttributes().getStats().getOrDefault("hp", 0);
        int vigorMana = vigor.getAttributes().getStats().getOrDefault("mana", 0);
        int greaterMana = greater.getAttributes().getStats().getOrDefault("mana", 0);

        assertTrue(greaterHp > vigorHp, "Greater Vigor Elixir must heal more hp than Elixir of Vigor");
        assertTrue(greaterMana > vigorMana, "Greater Vigor Elixir must restore more mana than Elixir of Vigor");
    }

    @Test
    void greaterAntivenom_curesPoisonAndHeals() throws RepositoryException {
        JsonItemRepository items = new JsonItemRepository(Path.of("data"));
        Item antivenom = items.findById(ItemId.of("greater-antivenom")).orElseThrow();

        assertTrue(antivenom.getAttributes().getStats().getOrDefault("hp", 0) > 0,
            "Greater Antivenom must heal, unlike the plain Cure Potion");
        assertTrue(antivenom.getEffects().stream().anyMatch(e ->
                e.id().equals(EffectId.of("poison")) && e.operation() == ItemEffectOperation.REMOVE),
            "Greater Antivenom must cure poison");
    }

    @Test
    void cookingFeasts_carryStrongerBuffsThanWellFed() throws Exception {
        JsonEffectRepository effects = new JsonEffectRepository(Path.of("."));
        EffectDefinition wellFed = effects.findById(EffectId.of("well-fed")).orElseThrow();
        EffectDefinition heartyFeast = effects.findById(EffectId.of("hearty-feast")).orElseThrow();
        EffectDefinition grandFeast = effects.findById(EffectId.of("grand-feast")).orElseThrow();

        assertNotNull(heartyFeast, "hearty-feast effect must exist");
        assertNotNull(grandFeast, "grand-feast effect must exist");
        assertTrue(statBonus(heartyFeast) > statBonus(wellFed),
            "hearty-feast must grant a stronger stat bonus than well-fed");
        assertTrue(grandFeast.durationTicks() > wellFed.durationTicks(),
            "grand-feast must last longer than well-fed");
        assertTrue(statBonus(grandFeast) > statBonus(heartyFeast),
            "grand-feast must grant a stronger stat bonus than hearty-feast");

        JsonItemRepository items = new JsonItemRepository(Path.of("data"));
        Item ferryFeast = items.findById(ItemId.of("ferryhands-feast")).orElseThrow();
        Item grandIsleFeast = items.findById(ItemId.of("grand-isle-feast")).orElseThrow();

        assertTrue(carriesEffect(ferryFeast, "hearty-feast"), "Ferryhand's Feast must apply hearty-feast");
        assertTrue(carriesEffect(grandIsleFeast, "grand-feast"), "Grand Isle Feast must apply grand-feast");
        assertTrue(grandIsleFeast.getAttributes().getStats().getOrDefault("hunger", 0)
                > ferryFeast.getAttributes().getStats().getOrDefault("hunger", 0),
            "Grand Isle Feast must restore more hunger than Ferryhand's Feast");
    }

    private boolean carriesEffect(Item item, String effectId) {
        for (ItemEffect effect : item.getEffects()) {
            if (effect.id().equals(EffectId.of(effectId))) {
                return true;
            }
        }
        return false;
    }
}
