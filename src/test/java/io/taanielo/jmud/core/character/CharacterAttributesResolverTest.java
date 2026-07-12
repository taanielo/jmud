package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CharacterAttributesResolverTest {

    private Race race(String id, AttributeBonus bonus) {
        return new Race(RaceId.of(id), id, 0, 50, 0, 0, 0, "", bonus);
    }

    private ClassDefinition classDef(String id, AttributeBonus bonus, AttributeGainSchedule gains) {
        return new ClassDefinition(
            ClassId.of(id), id, 0, 0, 0, List.of(), List.of(), "", LevelGains.DEFAULT, bonus, gains);
    }

    @Test
    void combinesBaselineRaceAndClassCreationBonusAtLevelOne() {
        Race orc = race("orc", new AttributeBonus(3, -2, 0, 0));
        ClassDefinition warrior = classDef("warrior",
            new AttributeBonus(2, 0, 0, 0), AttributeGainSchedule.NONE);
        CharacterAttributesResolver resolver =
            CharacterAttributesResolver.fromDefinitions(List.of(orc), List.of(warrior));

        CharacterAttributes attributes = resolver.resolve(RaceId.of("orc"), ClassId.of("warrior"), 1);

        // 10 baseline + 3 race + 2 class = 15 STR; 10 - 2 race = 8 INT; no level gains at level 1.
        assertEquals(15, attributes.strength());
        assertEquals(8, attributes.intellect());
        assertEquals(10, attributes.wisdom());
        assertEquals(10, attributes.agility());
    }

    @Test
    void appliesDeterministicLevelSchedule() {
        ClassDefinition warrior = classDef("warrior",
            new AttributeBonus(2, 0, 0, 0),
            new AttributeGainSchedule(
                AttributeGainCadence.EVERY_LEVEL,
                AttributeGainCadence.NONE,
                AttributeGainCadence.NONE,
                AttributeGainCadence.EVERY_3_LEVELS));
        CharacterAttributesResolver resolver =
            CharacterAttributesResolver.fromDefinitions(List.of(), List.of(warrior));

        CharacterAttributes atTen = resolver.resolve(null, ClassId.of("warrior"), 10);

        // 10 + 2 creation + 9 (every level past 1) = 21 STR; 10 + 3 (every 3 levels) = 13 AGI.
        assertEquals(21, atTen.strength());
        assertEquals(13, atTen.agility());
    }

    @Test
    void unknownAndNullIdsResolveToBaseline() {
        CharacterAttributesResolver resolver = CharacterAttributesResolver.baselineOnly();
        assertEquals(CharacterAttributes.baseline(), resolver.resolve(null, null, 50));
        assertEquals(CharacterAttributes.baseline(),
            resolver.resolve(RaceId.of("ghost"), ClassId.of("mystery"), 5));
    }
}
