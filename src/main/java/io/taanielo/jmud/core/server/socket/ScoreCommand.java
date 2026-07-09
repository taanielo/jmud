package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.combat.ClassArmorBonusResolver;
import io.taanielo.jmud.core.combat.EquipmentArmorResolver;
import io.taanielo.jmud.core.combat.RaceArmorBonusResolver;
import io.taanielo.jmud.core.player.LevelUpService;
import io.taanielo.jmud.core.player.LightingService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerSustenance;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Handles the {@code score} / {@code sc} command, displaying the player's level,
 * experience, XP needed for the next level, current vitals, and total armour class.
 */
public class ScoreCommand extends RegistrableCommand {

    private final EquipmentArmorResolver equipmentArmorResolver;
    private final RaceArmorBonusResolver raceArmorBonusResolver;
    private final ClassArmorBonusResolver classArmorBonusResolver;
    private final LightingService lightingService = new LightingService();

    /**
     * Creates a ScoreCommand that computes AC from the given resolvers.
     *
     * @param registry                the command registry to register with
     * @param equipmentArmorResolver  resolver for AC contributed by equipped armour items
     * @param raceArmorBonusResolver  resolver for AC contributed by the player's race
     * @param classArmorBonusResolver resolver for AC contributed by the player's class
     */
    public ScoreCommand(
            SocketCommandRegistry registry,
            EquipmentArmorResolver equipmentArmorResolver,
            RaceArmorBonusResolver raceArmorBonusResolver,
            ClassArmorBonusResolver classArmorBonusResolver) {
        super(registry);
        this.equipmentArmorResolver = Objects.requireNonNull(equipmentArmorResolver, "EquipmentArmorResolver is required");
        this.raceArmorBonusResolver = Objects.requireNonNull(raceArmorBonusResolver, "RaceArmorBonusResolver is required");
        this.classArmorBonusResolver = Objects.requireNonNull(classArmorBonusResolver, "ClassArmorBonusResolver is required");
    }

    @Override
    public String name() {
        return "score";
    }

    @Override
    public String shortDescription() {
        return "Display your level, XP, and current vitals. Aliases: SC";
    }

    @Override
    public String longDescription() {
        return "Usage: SCORE\n"
             + "  Shows your current level, experience points, HP, Mana, and Move.\n"
             + "  Alias: SC";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"SCORE".equals(token) && !"SC".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, this::handleScore));
    }

    private void handleScore(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to view your score.");
            return;
        }
        Player player = context.getPlayer();
        PlayerVitals vitals = player.getVitals();
        int level = player.getLevel();
        long xp = player.getExperience();
        long xpNeeded = LevelUpService.xpForNextLevel(level);
        long xpRemaining = Math.max(0L, xpNeeded - xp);
        int ac = equipmentArmorResolver.totalAc(player)
            + raceArmorBonusResolver.armorBonus(player)
            + classArmorBonusResolver.armorBonus(player);

        context.writeLineSafe("--- Score ---");
        context.writeLineSafe(String.format("Level : %d", level));
        context.writeLineSafe(String.format("XP    : %d / %d  (%d to next level)", xp, xpNeeded, xpRemaining));
        context.writeLineSafe(String.format("HP    : %d / %d", vitals.hp(), vitals.maxHp()));
        context.writeLineSafe(String.format("Mana  : %d / %d", vitals.mana(), vitals.maxMana()));
        context.writeLineSafe(String.format("Move  : %d / %d", vitals.move(), vitals.maxMove()));
        context.writeLineSafe(String.format("Hunger: %d / %d", player.getSustenance().hunger(), PlayerSustenance.MAX));
        context.writeLineSafe(String.format("Thirst: %d / %d", player.getSustenance().thirst(), PlayerSustenance.MAX));
        context.writeLineSafe(String.format("Gold  : %d", player.getGold()));
        context.writeLineSafe(String.format("Kills : %d", player.getTotalKills()));
        context.writeLineSafe(String.format("Pracs : %d", player.getPracticePoints()));
        context.writeLineSafe(String.format("AC    : %d", ac));
        lightingService.brightestLightSource(player).ifPresent(light ->
            context.writeLineSafe(String.format("Light : %s (radius %d)", light.getName(),
                lightingService.carriedLightRadius(player))));
        List<String> titles = player.titles().earned();
        if (!titles.isEmpty()) {
            context.writeLineSafe("Titles: " + String.join(", ", titles));
        }
        context.sendPrompt();
    }
}
