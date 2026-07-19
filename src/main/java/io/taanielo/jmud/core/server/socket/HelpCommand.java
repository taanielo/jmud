package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.character.AttributeBonus;
import io.taanielo.jmud.core.character.AttributeGainCadence;
import io.taanielo.jmud.core.character.AttributeGainSchedule;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.LevelGains;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.player.DeathSettings;

/**
 * Handles the {@code HELP} / {@code H} command, giving players a way to
 * discover all available commands and their descriptions, and to look up a
 * class reference sheet.
 *
 * <p>Three forms are supported:
 * <ul>
 *   <li>{@code HELP}       — prints a sorted list of all registered command names
 *       together with their {@link SocketCommandHandler#shortDescription()}.</li>
 *   <li>{@code HELP <command>} — prints the {@link SocketCommandHandler#longDescription()}
 *       for the named command.</li>
 *   <li>{@code HELP <class>} — prints the reference sheet for a playable class
 *       (role/playstyle, starting ability kit, trainable pool, per-level vitals gains and
 *       attribute bonuses/growth), resolved from the class data. Class names are only consulted
 *       when the argument does not match a registered command, so class names never clutter the
 *       {@code HELP} command listing.</li>
 * </ul>
 *
 * <p><strong>Lookup precedence for {@code HELP <name>}:</strong> a registered command is matched
 * first (case-insensitively); only if no command matches is {@code name} resolved against the
 * class registry (by class id or display name, case-insensitively). If neither matches, the
 * standard {@code No help available for '<name>'.} message is shown.
 *
 * <p>The command list is derived from the registry snapshot at the time the
 * command is executed, so newly registered commands are visible immediately.
 */
public class HelpCommand extends RegistrableCommand {

    private static final List<String> RESISTANCE_TOPIC = List.of(
        "Elemental Resistances",
        "  Not all damage is alike. Most weapons deal ordinary physical damage, but some",
        "  attacks — a fire wyrm's cinder breath, a frost wyrm's glacial breath, a void",
        "  wraith's warmth-devouring dark — deal a specific element: FIRE, COLD, or POISON.",
        "",
        "  Armour can carry a matching resistance stat, e.g. fire_resist or cold_resist, that",
        "  reduces incoming damage of that element by that percentage. Resistances from every",
        "  equipped piece add together, but are capped so an elemental blow always deals at",
        "  least some damage — resistance never grants full immunity. Physical damage is never",
        "  reduced by resistance.",
        "",
        "  Seek out zone-appropriate gear: cold-resist gear near the Frozen Peaks and the",
        "  Voidscar, fire-resist gear in the Emberdeep, Cinder Reaches, and beneath them.",
        "",
        "  EXAMINE an item to see its resistance stats, COMPARE two items to weigh a swap, and",
        "  EQUIPMENT to review the resistances your worn gear currently provides."
    );

    private static final List<String> HAZARDS_TOPIC = List.of(
        "Environmental Hazards",
        "  Not every danger has teeth. Some rooms are lethal terrain in their own right: the",
        "  Emberdeep's cooled-lava passages, the Frozen Peaks' wind-scoured ridges, the Undercity",
        "  Sewers' gas-choked tunnels. Stand in one and the room itself hurts you, tick after tick,",
        "  for as long as you remain.",
        "",
        "  A hazard is never a surprise. A hazardous room always names its danger plainly in its",
        "  description with a line like:",
        "",
        "    (Hazard) A lethal fire hazard fills this room; matching resistance gear reduces the damage.",
        "",
        "  so you can always retreat, gear up, or press on with full information.",
        "",
        "  Hazard damage is typed — FIRE, COLD, or POISON — and is mitigated by the very same",
        "  elemental-resistance gear that protects you from a mob's matching attack (see HELP",
        "  resistances). A fire-resist cloak that shrugs off a fire wyrm's breath also shrugs off a",
        "  lava passage's searing fumes, capped so resistance never grants full immunity. It plugs",
        "  into the same HP path as combat: it can knock you out and kill you, and it interrupts a",
        "  spell you are channeling. Gear for the zone before you enter it, not just before the boss.");

    private static final List<String> WORLD_EVENTS_TOPIC = List.of(
        "World Events",
        "  Every so often the world tears. On a randomized timer a WORLD EVENT opens: a rare,",
        "  elite monster is torn into a fixed room in one of a handful of eligible zones — from",
        "  the Darkwood Wilds up through the Frozen Peaks and into the Voidscar — and every online",
        "  player hears about it at once with a server-wide line like:",
        "",
        "    A crack of unnatural energy tears open in the Glacier — the Rimewrought Stalker",
        "    has emerged!",
        "",
        "  These elites hit far harder and soak far more punishment than the ordinary residents of",
        "  their zone, but they drop a guaranteed, high-value reward — rare or epic loot plus bonus",
        "  gold and experience — so they are well worth dropping what you are doing to hunt.",
        "",
        "  The window is short. If no one brings the elite down within its span the rift collapses",
        "  on its own, the monster fades away with no kill credit, and everyone is told:",
        "",
        "    The rift over the Glacier collapses — the Rimewrought Stalker fades away.",
        "",
        "  Only one world event is ever open at a time, so when the call goes out, it is the moment",
        "  to move. Whoever lands the killing blow is named to the whole server.");

    private static final List<String> COMBAT_TOPIC = List.of(
        "Combat: Hits, Crits, and Defence",
        "  Every blow is rolled, not guaranteed — against other players AND against the monsters",
        "  you spend most of your time fighting. When you attack a mob you can miss outright, and",
        "  a landed blow can turn into a critical hit for bonus damage; sharper weapons (a higher",
        "  hit bonus) and greater agility improve both.",
        "",
        "  Defence matters just as much on the receiving end. A mob's swing is rolled against your",
        "  total armour class, so worn armour makes monsters miss you more often — it is not a",
        "  duel-only stat. A shield in your off-hand can outright BLOCK an incoming hit, cutting the",
        "  damage it would have dealt. Elemental resistances (see HELP resistances) then reduce any",
        "  remaining typed damage on top of that.",
        "",
        "  So gear up for the fight you are actually in: better armour, a shield, and agility make",
        "  you harder to hit and harder to hurt against mobs, not merely in the arena.",
        "",
        "  PARRY is the shieldless defender's answer. If you are wielding a melee weapon in your main",
        "  hand — a two-hander, a dual-wield main weapon, or any blade a caster carries — you have a",
        "  chance, scaled by your agility, to PARRY an incoming melee hit outright. A parried blow",
        "  deals you zero damage (it is checked before, and instead of, a shield block on that swing)",
        "  and you immediately RIPOSTE: a free counter-strike with your own weapon, mitigated by your",
        "  foe's armour just like a normal hit. This is why some hits land for nothing and answer",
        "  with a sudden blow of your own. Parry never triggers on a ranged shot, and unarmed",
        "  defenders cannot parry.",
        "",
        "  And it cuts both ways: a few monsters fight defensively and can PARRY your own melee",
        "  attacks. Armoured guards, trained humanoid soldiers, and boss-tier elites may turn your",
        "  blade aside for no damage and riposte you in return. Only your melee swings are eligible —",
        "  a ranged shot, an area spell, or a summoned pet's attack is never parried — so size a",
        "  foe up before you wade in: the one that fights back smarter is the one to respect.",
        "",
        "  Watch the company a monster keeps. Some creatures run in PACKS: a solo-looking pull can",
        "  wake its neighbours. Strike one wolf in a den and the rest snarl and lunge to its",
        "  defence, piling onto you as extra attackers. Thin the pack before you commit — a stealth",
        "  pull, a crowd-control spell, or an area attack that hits several at once turns a deadly",
        "  swarm into a fair fight. A packmate only joins a fight already begun; it never ambushes",
        "  you first, so hidden rogues still choose their moment.",
        "",
        "  A few boss-tier foes TELEGRAPH a devastating signature attack. Instead of landing it",
        "  instantly, the boss spends a turn winding up and announces what is coming (\"...begins",
        "  gathering the Final Chord...\"), giving you a few ticks of warning before the blow falls.",
        "  Use them: FLEE the room to dodge it outright, top yourself off with a heal, or pop a",
        "  defensive cooldown so the hit lands on a braced target. Best of all, a well-timed hard STUN",
        "  (HAMMER OF JUSTICE, KIDNEY SHOT, or any stunning ability) landed on the boss mid-wind-up",
        "  interrupts the channel outright — the signature attack is cancelled and never lands, so save",
        "  a stun for the telegraph window instead of burning it early. If the boss dies or you leave the",
        "  fight before the wind-up finishes, the attack simply never lands. Watch for the warning —",
        "  the scariest hit in the fight is the one you can see coming.",
        "",
        "  The greatest bosses also ENRAGE if you let a fight drag on. Grind one of them down purely",
        "  by turtling — heavy healing, kiting, hiding behind block and parry — and eventually its",
        "  patience breaks: it announces the turn (\"...its eyes blaze with fury — it grows enraged!\")",
        "  and hits markedly harder for the rest of that fight. This is why sustain alone is not enough",
        "  against a capstone boss: bring your burst cooldowns and end the fight before the clock runs",
        "  out. CONSIDER a foe first — if it \"will wear down slowly, then grow dangerous,\" it enrages,",
        "  so plan to spike it down rather than out-heal it. Enrage is per-fight: break off cleanly (or",
        "  kill it) and a fresh pull starts calm again, its patience reset.");

    private static final List<String> COOLDOWNS_TOPIC = List.of(
        "Cooldowns",
        "  Usage: COOLDOWNS (alias CD)",
        "",
        "  Lists every ability you have learned in a table with three columns:",
        "    Ability — the ability's display name.",
        "    Type    — SKILL or SPELL.",
        "    Status  — its live readiness: 'Ready' when it can be used right now, or",
        "              '<n> ticks' remaining until it comes off cooldown.",
        "",
        "  Unlike ABILITIES, which shows each ability's base cooldown length, COOLDOWNS",
        "  reads your own live cooldown timers so you can plan an opener or rotation",
        "  instead of guessing. It is read-only and never changes game state.");

    private static final List<String> GUILD_WAR_TOPIC = List.of(
        "Guild Wars",
        "  A guild war is a declared, consensual rivalry between two guilds, scored by DUEL wins",
        "  between their members. It is prestige only — no gold or items ever change hands.",
        "",
        "  Declaring: GUILD WAR <guild> — a guild leader challenges a rival guild. That guild's",
        "  leader has 60 seconds to answer with GUILD WAR ACCEPT (the war begins) or GUILD WAR",
        "  DECLINE (it is refused). Both propose and accept are leader-only, and a guild may be in",
        "  only one war at a time — on either side. If the target never answers, the declaration",
        "  quietly expires.",
        "",
        "  Scoring: while the war runs, any consensual DUEL that resolves between a member of one",
        "  warring guild and a member of the other awards one war point to the winner's guild — on",
        "  top of the duel's usual outcome (rankings, any DUEL WAGER gold). Membership is checked",
        "  live at the moment the duel resolves, so someone who has left either guild no longer",
        "  scores, and duels within the same guild or against outsiders never count.",
        "",
        "  Winning: the first guild to 5 war points wins automatically. A server-wide announcement",
        "  fires and the winning guild's lifetime war-win count rises (shown in RANK GUILDS).",
        "",
        "  GUILD WAR CONCEDE — either leader may forfeit an active war early; the rival guild is",
        "  credited the win with no threshold needed.",
        "  GUILD WAR STATUS  — (or plain GUILD WAR while at war) shows the opponent, each side's",
        "  current score, and the target to win.");

    private static final List<String> DEATH_TOPIC = List.of(
        "Death & Recovery",
        "  When your HP hits 0 you die. You collapse where you fell, and after a short delay you",
        "  awaken back at your recall point — the Training Yard by default, or wherever you have",
        "  BIND-anchored your recall (see HELP recall). Death is never the end of your character;",
        "  it is a setback you recover from.",
        "",
        "  Newbie grace. Below level " + DeathSettings.graceLevel() + " you are protected: dying keeps"
            + " all your gold and items,",
        "  so an early death costs you nothing but the walk back. This grace is temporary — from",
        "  level " + DeathSettings.graceLevel() + " onward the full corpse mechanic applies, so learn"
            + " the recovery tools now,",
        "  while mistakes are cheap.",
        "",
        "  Corpses. At or above the grace level, dying drops your carried gold and items into a",
        "  corpse in the room where you fell. Your corpse decays after "
            + DeathSettings.corpseDecaySeconds() + " seconds and is then",
        "  gone for good, so recover it promptly. Type CORPSE and you are walked back to it",
        "  step by step; once there, LOOT it to reclaim everything.",
        "",
        "  Resurrection. A Cleric who knows the RESURRECT spell can raise a fallen party member,",
        "  restoring them to life without the full walk-back. Grouping with a healer is the surest",
        "  insurance against a costly corpse run.");

    /**
     * Static concept help topics (not backed by a command or class), keyed by the lower-cased topic
     * name and its aliases. Looked up after commands but before classes, so {@code HELP resistances}
     * explains a game system that no single command owns.
     */
    private static final Map<String, List<String>> TOPICS = Map.ofEntries(
        Map.entry("resistances", RESISTANCE_TOPIC),
        Map.entry("resistance", RESISTANCE_TOPIC),
        Map.entry("resist", RESISTANCE_TOPIC),
        Map.entry("hazard", HAZARDS_TOPIC),
        Map.entry("hazards", HAZARDS_TOPIC),
        Map.entry("environmental hazards", HAZARDS_TOPIC),
        Map.entry("world events", WORLD_EVENTS_TOPIC),
        Map.entry("world event", WORLD_EVENTS_TOPIC),
        Map.entry("worldevents", WORLD_EVENTS_TOPIC),
        Map.entry("worldevent", WORLD_EVENTS_TOPIC),
        Map.entry("events", WORLD_EVENTS_TOPIC),
        Map.entry("event", WORLD_EVENTS_TOPIC),
        Map.entry("combat", COMBAT_TOPIC),
        Map.entry("armour", COMBAT_TOPIC),
        Map.entry("armor", COMBAT_TOPIC),
        Map.entry("shield", COMBAT_TOPIC),
        Map.entry("shields", COMBAT_TOPIC),
        Map.entry("block", COMBAT_TOPIC),
        Map.entry("parry", COMBAT_TOPIC),
        Map.entry("riposte", COMBAT_TOPIC),
        Map.entry("enrage", COMBAT_TOPIC),
        Map.entry("enrages", COMBAT_TOPIC),
        Map.entry("telegraph", COMBAT_TOPIC),
        Map.entry("cooldowns", COOLDOWNS_TOPIC),
        Map.entry("cooldown", COOLDOWNS_TOPIC),
        Map.entry("cd", COOLDOWNS_TOPIC),
        Map.entry("guild war", GUILD_WAR_TOPIC),
        Map.entry("guild wars", GUILD_WAR_TOPIC),
        Map.entry("guildwar", GUILD_WAR_TOPIC),
        Map.entry("guildwars", GUILD_WAR_TOPIC),
        Map.entry("death", DEATH_TOPIC),
        Map.entry("dying", DEATH_TOPIC),
        Map.entry("respawn", DEATH_TOPIC)
    );

    private final SocketCommandRegistry registry;
    private final ClassRepository classRepository;
    private final AbilityRegistry abilityRegistry;

    /**
     * Creates a {@code HelpCommand} and registers it with the given registry.
     *
     * @param registry        the registry that this command is part of
     * @param classRepository repository used to resolve {@code HELP <class>} reference sheets
     * @param abilityRegistry registry used to resolve ability ids to their display names for the
     *                        class reference sheet
     */
    public HelpCommand(
        SocketCommandRegistry registry,
        ClassRepository classRepository,
        AbilityRegistry abilityRegistry
    ) {
        super(registry);
        this.registry = registry;
        this.classRepository = classRepository;
        this.abilityRegistry = abilityRegistry;
    }

    /**
     * Creates a {@code HelpCommand} without class-lookup support (command help only).
     *
     * <p>Used where no class data is available; {@code HELP <class>} degrades to the standard
     * not-found message. Production wiring uses
     * {@link #HelpCommand(SocketCommandRegistry, ClassRepository, AbilityRegistry)}.
     *
     * @param registry the registry that this command is part of
     */
    public HelpCommand(SocketCommandRegistry registry) {
        this(registry, null, null);
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String shortDescription() {
        return "List available commands, describe a command, or view a class. Aliases: H";
    }

    @Override
    public String longDescription() {
        return """
               Usage: HELP  |  HELP <command>  |  HELP <class>
                 HELP          — show a sorted list of all commands with short descriptions.
                 HELP <name>   — show the full description for the named command.
                 HELP <class>  — show a class reference sheet (role, starting kit, trainable
                                 abilities, level gains and attribute growth), e.g. HELP warrior.
               A name is matched as a command first, then as a class.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"HELP".equals(token) && !"H".equals(token)) {
            return Optional.empty();
        }
        String arg = parts[1].trim();
        if (arg.isBlank()) {
            return Optional.of(new SocketCommandMatch(this, this::handleListAll));
        }
        String target = arg;
        return Optional.of(new SocketCommandMatch(this, context -> handleDetail(context, target)));
    }

    /**
     * Sends a sorted list of all commands with their short descriptions.
     */
    private void handleListAll(SocketCommandContext context) {
        List<SocketCommandHandler> sorted = registry.commands().stream()
                .sorted(Comparator.comparing(SocketCommandHandler::name))
                .toList();

        context.writeLineSafe("Available commands:");
        for (SocketCommandHandler handler : sorted) {
            String short_ = handler.shortDescription();
            if (short_.isBlank()) {
                context.writeLineSafe("  " + handler.name());
            } else {
                context.writeLineSafe(String.format("  %-12s %s", handler.name(), short_));
            }
        }
        context.writeLineSafe(
            "Topics: HELP combat, HELP resistances, HELP hazards, HELP world events, HELP death");
        context.sendPrompt();
    }

    /**
     * Sends the long description for the named command, falling back to a class reference sheet,
     * then to an error message.
     *
     * @param context the command execution context
     * @param name    the command or class name to look up (case-insensitive)
     */
    private void handleDetail(SocketCommandContext context, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        Optional<SocketCommandHandler> found = registry.commands().stream()
                .filter(h -> h.name().equalsIgnoreCase(lower))
                .findFirst();

        if (found.isPresent()) {
            SocketCommandHandler handler = found.get();
            String desc = handler.longDescription();
            if (desc.isBlank()) {
                context.writeLineWithPrompt("No detailed help available for '" + handler.name() + "'.");
                return;
            }
            for (String line : desc.split("\n", -1)) {
                context.writeLineSafe(line);
            }
            context.sendPrompt();
            return;
        }

        List<String> topic = TOPICS.get(lower);
        if (topic != null) {
            for (String line : topic) {
                context.writeLineSafe(line);
            }
            context.sendPrompt();
            return;
        }

        Optional<ClassDefinition> classDef = resolveClass(name);
        if (classDef.isPresent()) {
            renderClassHelp(context, classDef.get());
            return;
        }

        context.writeLineWithPrompt("No help available for '" + name + "'.");
    }

    /**
     * Resolves a class by id or display name, case-insensitively. Since every class's data file
     * names it after its id (lower-cased), a cache-backed {@code findById} lookup on the normalised
     * argument matches both the {@code warrior} and {@code Warrior} forms without scanning all
     * class files on the tick thread.
     */
    private Optional<ClassDefinition> resolveClass(String name) {
        if (classRepository == null) {
            return Optional.empty();
        }
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            return classRepository.findById(ClassId.of(lower));
        } catch (IllegalArgumentException | ClassRepositoryException e) {
            return Optional.empty();
        }
    }

    /**
     * Writes the reference sheet for a class: role/playstyle description, starting ability kit,
     * trainable ability pool, per-level vitals gains and creation attribute bonuses/growth.
     */
    private void renderClassHelp(SocketCommandContext context, ClassDefinition cd) {
        context.writeLineSafe(cd.name());
        if (!cd.description().isBlank()) {
            context.writeLineSafe("  " + cd.description().strip());
        }
        context.writeLineSafe("  Starting abilities: " + abilityNames(cd.startingAbilityIds()));
        List<AbilityId> trainable = cd.trainableAbilityIds();
        if (!trainable.isEmpty()) {
            context.writeLineSafe("  Trainable via TRAIN: " + abilityNames(trainable));
        }
        LevelGains gains = cd.levelGains();
        context.writeLineSafe(String.format(
            "  Level gains: +%d HP, +%d mana, +%d move per level",
            gains.hp(), gains.mana(), gains.move()));
        String bonus = formatAttributeBonus(cd.attributeBonus());
        if (!bonus.isEmpty()) {
            context.writeLineSafe("  Attribute bonuses (creation): " + bonus);
        }
        String growth = formatAttributeGains(cd.attributeGains());
        if (!growth.isEmpty()) {
            context.writeLineSafe("  Attribute growth: " + growth);
        }
        context.sendPrompt();
    }

    private String abilityNames(List<AbilityId> abilityIds) {
        if (abilityIds.isEmpty()) {
            return "none";
        }
        return abilityIds.stream()
                .map(this::abilityDisplayName)
                .collect(Collectors.joining(", "));
    }

    private String abilityDisplayName(AbilityId abilityId) {
        if (abilityRegistry == null) {
            return abilityId.getValue();
        }
        return abilityRegistry.findById(abilityId)
                .map(Ability::name)
                .orElse(abilityId.getValue());
    }

    private String formatAttributeBonus(AttributeBonus bonus) {
        List<String> parts = new ArrayList<>();
        appendSignedAttribute(parts, "STR", bonus.strength());
        appendSignedAttribute(parts, "INT", bonus.intellect());
        appendSignedAttribute(parts, "WIS", bonus.wisdom());
        appendSignedAttribute(parts, "AGI", bonus.agility());
        return String.join(", ", parts);
    }

    private void appendSignedAttribute(List<String> parts, String label, int value) {
        if (value > 0) {
            parts.add("+" + value + " " + label);
        } else if (value < 0) {
            parts.add(value + " " + label);
        }
    }

    private String formatAttributeGains(AttributeGainSchedule schedule) {
        List<String> parts = new ArrayList<>();
        appendCadence(parts, "STR", schedule.strength());
        appendCadence(parts, "INT", schedule.intellect());
        appendCadence(parts, "WIS", schedule.wisdom());
        appendCadence(parts, "AGI", schedule.agility());
        return String.join(", ", parts);
    }

    private void appendCadence(List<String> parts, String label, AttributeGainCadence cadence) {
        String description = switch (cadence) {
            case NONE -> null;
            case EVERY_LEVEL -> "every level";
            case EVERY_2_LEVELS -> "every 2 levels";
            case EVERY_3_LEVELS -> "every 3 levels";
        };
        if (description != null) {
            parts.add(label + " " + description);
        }
    }
}
