# Feature Completeness Matrix

Tracks how complete each shipped content entity and system is against
[`content-dod.md`](content-dod.md). **Maintenance rule (AGENTS.md §11): any PR that
adds or changes game content must update the affected rows in the same PR.**
The game-designer agent reads this file at the start of every cycle and
prioritizes closing gaps over proposing new content.

Legend: ✅ done · 🔨 open issue filed (referenced) · ❌ gap, no issue yet · — not applicable

Machine-checkable [V] aspects disappear from this file once the corresponding
`--validate-data` rule exists (the validator then owns them); this matrix tracks
what validation cannot.

_Last full audit: 2026-07-13 (seeded by hand)._

## Classes

| Class | L1 offense | Trainable pool | Level gains | Attributes | Creation description | HELP entry | Balance pass |
|---|---|---|---|---|---|---|---|
| adventurer | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| bard | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| cleric | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| druid | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| mage | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| necromancer | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| paladin | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| ranger | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| rogue | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| shaman | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |
| warrior | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ✅ #556 |

Balance-pass niche rationale (baseline for future passes, #556). Every class's
`level_gains` share a fixed 18-point budget (hp+mana+move), so no class is strictly
stronger — only the distribution, carry, healing, attribute bonuses and kit size differ
by archetype. For no archetype is one class's full package (stats + total kit size) a
superset of a peer's:

- **melee-str — warrior vs. paladin.** Warrior is the pure bruiser: top HP (13/lvl), top
  carry (20), best self-heal (+3), but the lightest attribute bonus (`str +2`) and only 4
  abilities. Paladin trades raw HP/carry for `armor +5`, extra mana, a third attribute
  point (`str +2, wis +1`) and a fifth ability (divine shield / lay on hands) — hybrid
  durability, not a warrior upgrade.
- **caster-int — mage vs. necromancer.** Previously an identical stat block with an
  unequal kit; now split. Mage is the fragile nuker: lowest HP (6/lvl), highest mana
  (9/lvl), the deepest toolkit (4 starting + 3 trainable = 7 abilities). Necromancer trades
  spell mana (7/lvl) and two abilities (5 total) for sturdier health (8/lvl), extra carry
  (6) and matching self-sustain via life drain (`healing -1`) — the durable attrition
  caster who stands behind its pets. Neither package is a superset of the other.
- **caster-wis-hybrid — cleric vs. druid vs. shaman.** Cleric is the dedicated healer:
  best healing (+2), most carry (10) and the biggest kit (4 starting, incl. resurrection),
  paid for with the slowest move of any class (2/lvl). Druid keeps normal move (3) and a
  higher mana pool for nature damage; shaman keeps normal move (3) and the extra HP of a
  totem hybrid (8/lvl) with three starting abilities. Each wins at least one axis (move or
  mana or HP) that cleric does not.
- **agi-skirmish — rogue vs. ranger.** Rogue is the burst/utility skirmisher: highest HP
  in the pair (10/lvl), most carry (8), and the largest kit (4 starting: backstab, envenom,
  pick, sneak). Ranger is the mobility/reach specialist: the fastest class afoot (unique
  move 5/lvl) with more mana (5/lvl), trading HP and kit size for unmatched mobility and
  ranged tracking. Rogue does not dominate ranger's move/mana; ranger does not dominate
  rogue's HP/carry/kit.
- **generalist / support outliers.** Adventurer is the deliberately forgiving beginner
  (lightest `str +1, int +1` bonus, balanced 10/5/3 vitals, bash + self-heal). Bard is the
  fragile group-support caster (spread `agi +1, int +1, wis +1`, buff/debuff songs); neither
  shares an archetype pair, so neither can be a superset of a peer.

Veteran tier (#573). Every class now has a third trainable ability gated at **level 15**, so
progression is no longer flat past level 5 and practice points earned in the midgame have a
`TRAIN LIST` payoff. Each veteran ability fits its class niche and introduces a new mechanic
rather than a bigger number on an existing one: warrior `skill.execute` (heavy finisher),
adventurer `skill.overpower` (heavy strike), rogue `skill.eviscerate` (rupture bleed), ranger
`skill.crippling-shot` (attack/accuracy debuff), mage `spell.frostbolt` (damage + freeze),
cleric `spell.searing-light` (holy damage vs. the living), druid `spell.thornlash` (entangle
bleed + defense debuff), bard `spell.dissonant-chord` (demoralize debuff), shaman
`spell.flame-shock` (scorch burn), necromancer `spell.soul-rot` (heavy nuke + defense rot) and
paladin `spell.guardian-aura` (party-wide defensive aegis). `HELP <class>` and `TRAIN LIST`
pick these up automatically from `trainable_ability_ids`.

Master tier (#597). Every class now has a fourth trainable ability gated at **level 30**, landing
at the Cinder Reaches / Voidscar threshold so the 16-48 stretch of the leveling curve is no longer
flat. Each master ability introduces a new mechanic for its class rather than a bigger number:
warrior `skill.rallying-cry` (party attack/defense buff — the warrior's first group support),
adventurer `skill.disarming-strike` (damage + attack/accuracy debuff), rogue `skill.ambush` (heavy
stealth opener + daze), ranger `skill.volley` (the ranger's first AoE — a room-wide arrow storm),
mage `spell.arcane-shackles` (root/control debuff distinct from frostbolt's freeze), necromancer
`spell.plague-cloud` (AoE life-drain), cleric `spell.sanctuary` (party-wide ward: armor + heal over
time), druid `spell.bear-form` (offensive shapeshift buff: attack + armor), shaman
`spell.stormcall-totem` (party attack/crit totem buff), bard `spell.anthem-of-renewal` (party
heal-over-time + defense song) and paladin `spell.holy-wrath` (holy damage vs. any foe + a burning
radiant brand). Nine new status effects back the buff/debuff abilities; the two AoE abilities and
`skill.ambush` reuse the direct-damage / effect pipeline. `HELP <class>` and `TRAIN LIST` pick these
up automatically from `trainable_ability_ids`.

Grandmaster tier (#643). Every class now has a fifth trainable ability gated at **level 45**, landing
at the Marrow Bloom / Bonelight Choir / Unsung threshold so the 31-64 endgame stretch — over half the
current level range — is no longer flat past the master tier. Each grandmaster ability introduces a
new mechanic for its class rather than a bigger number: warrior `skill.sunder-armor` (armor-shred
defense debuff), adventurer `skill.brace` (reactive self defensive stance), rogue `skill.smoke-bomb`
(escape/concealment self-buff), ranger `skill.hunters-mark` (marking debuff that bares a foe's
defences), mage `spell.silence` (anti-caster control distinct from frostbolt's freeze and shackles'
root), necromancer `spell.dark-empowerment` (party/minion attack+crit empowerment), cleric
`spell.purify` (party-wide cleansing heal burst), druid `spell.wild-growth` (group heal-over-time),
shaman `spell.healing-totem` (a second, defensive/healing totem to pair with stormcall), bard
`spell.war-song` (party attack/accuracy offense song) and paladin `spell.consecration` (the paladin's
first AoE — a room-wide burst of holy damage). Ten new status effects back the buff/debuff abilities;
`spell.consecration` reuses the AoE direct-damage pipeline. `HELP <class>` and `TRAIN LIST` pick these
up automatically from `trainable_ability_ids`.

Legendary tier (#665). Every class now has a sixth trainable ability gated at **level 60**, landing
mid-Unsung (57-64) one zone before the level-72 Undersong cap, so the entire 45-72 endgame stretch
opened up by The Unsung and The Undersong is no longer flat past the grandmaster tier. Each legendary
ability introduces a new mechanic for its class rather than a bigger number: warrior `skill.whirlwind`
(the warrior's first AoE — a spinning strike across every foe), adventurer `skill.field-dressing` (the
generalist's first targeted-ally heal, a wound-binding heal-over-time), rogue `skill.garrote` (a
choking silence/control debuff distinct from ambush's daze), ranger `skill.evasive-maneuvers` (a
personal evasion/defense self-buff), mage `spell.mana-shield` (a self-buff damage-mitigation barrier
distinct from stoneskin's flat armor), necromancer `spell.bone-shield` (the necromancer's own personal
defensive buff), cleric `spell.holy-nova` (the cleric's first AoE — a bursting nova of holy damage),
druid `spell.hurricane` (the druid's first AoE — a storm of nature damage), shaman `spell.chain-heal`
(a direct heal-the-whole-group spell to pair with the totems), bard `spell.siren-song` (the bard's
first AoE — a mind-assailing song against every foe) and paladin `spell.avenging-wrath` (a self-buff
spiking the paladin's own damage and healing). Six new status effects back the
buff/debuff/HoT abilities (`garrote`, `evasive-maneuvers`, `mana-shield`, `bone-shield`,
`field-dressing`, `avenging-wrath`); the four AoE abilities (`skill.whirlwind`, `spell.holy-nova`,
`spell.hurricane`, `spell.siren-song`) reuse the AoE direct-damage pipeline. `HELP <class>` and
`TRAIN LIST` pick these up automatically from `trainable_ability_ids`.

Mythic tier (#715). Every class now has a seventh trainable ability gated at **level 75**, landing
mid-Interval (73-80) one zone before the level-88 Coda cap, so the entire 60-88 endgame stretch opened
up by The Undersong, The Interval and The Coda is no longer flat past the legendary tier. Each mythic
ability introduces a new mechanic for its class rather than a bigger number: warrior
`skill.intimidating-shout` (the warrior's first crowd control — a room-wide fear that fouls every foe's
aim), adventurer `skill.sweeping-strike` (the generalist's first AoE — a wide swing across every foe),
rogue `skill.blade-flurry` (the rogue's first AoE — a whirling flurry of blades across every foe),
ranger `skill.pinning-shot` (the ranger's first single-target root/control), mage `spell.meteor-swarm`
(the mage's first AoE — a rain of fire across every foe), necromancer `spell.doom` (the necromancer's
first AoE control — a room-wide curse of dread sapping aim and strength), cleric `spell.mass-exorcism`
(the cleric's first AoE control — a searing wave of holy light that routs every foe), druid
`spell.entangling-roots` (the druid's first AoE control — grasping vines that root every foe), shaman
`spell.earthbind-totem` (the shaman's first AoE control — a third totem that roots and slows every
foe), bard `spell.virtuoso` (the bard's first personal power buff — a solo spiking its own attack and
spellcraft) and paladin `spell.hammer-of-justice` (the paladin's first control — a single-target
stun). Eight new status effects back the control/buff abilities (`intimidated`, `pinned`, `doomed`,
`exorcised`, `rooted`, `earthbound`, `virtuoso`, `hammer-of-justice`); the AoE abilities reuse the AoE
direct-damage/effect pipeline and the single-target control abilities follow the arcane-shackles
damage+effect pattern. `HELP <class>` and `TRAIN LIST` pick these up automatically from
`trainable_ability_ids`.

## Races

| Race | Stat profile | Creation description | Attribute bonuses | Balance pass |
|---|---|---|---|---|
| dwarf | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |
| elf | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |
| gnome | ✅ #679 | ✅ #679 | ✅ #679 | ✅ #679 |
| halfling | ✅ #615 | ✅ #615 | ✅ #615 | ✅ #615 |
| human | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |
| orc | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |
| troll | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |

Balance-pass niche rationale (baseline for future passes, #554). Each race has a
distinct, non-dominated package — for no single class archetype (melee str-build,
caster int/wis-build, agi-build) is one race's full package a superset of another's:

- **human** — the safe generalist: `agi +1`, `carry 60`, and *no* penalty anywhere. Its
  upside is genuine (universal agility edge + mid-pack carry) but the least extreme, so
  it is never the strictly-weakest pick yet never a superset of a specialist either.
- **elf** — the caster: `int +2`, `agi +2`, `mana +10`, paid for with `str -2`,
  `healing -2`, `attack -1` and the lowest carry (40).
- **dwarf** — the tank: `armor +2`, top carry (100), `str +2`, `wis +1`, offset by `agi -1`.
- **orc** — burst melee dps: `str +3`, `attack +5`, carry 90, paid for with `healing -2`,
  `mana -5`, `int -2`.
- **troll** — attrition/sustain melee: `str +3`, `healing +2`, carry 80, but crude
  `attack -3` (a real melee drawback, unlike the melee-irrelevant `int -3`) plus `int -3`,
  so it outlasts rather than out-damages orc's burst.
- **halfling** (#615) — the agility specialist: the highest agility bonus in the game
  (`agi +3`, edging out elf's `+2`) driving accuracy/dodge/crit, paid for with `str -2`
  and a light `carry 45`, and *no* caster upside (mana/int/attack/healing all default 0).
  vs. **human**: out-agi's the safe generalist (`+3` vs `+1`) but carries less (45 vs 60)
  and takes a real `str -2` human lacks — human stays the no-penalty pick, halfling the
  higher-ceiling specialist. vs. **elf**: beats elf's agility by 1 with zero caster
  package, so elf still wins outright for a caster build while halfling wins outright for
  a pure-martial agi build (rogue backstab/crit, ranger marksman) — neither package is a
  superset of the other. vs. the str-based dwarf/orc/troll it competes on a different
  axis entirely.
- **gnome** (#679) — the wisdom/healing support caster, closing the last archetype gap
  (cleric/druid/shaman had no race built around their wisdom stat): the highest wisdom
  bonus in the game (`wis +3`, tripling dwarf's incidental `+1`), best-in-class
  `healing +2` (tying troll), a modest `mana +5`, paid for with the steepest strength
  penalty of any race (`str -3`), `attack -2`, and the lightest carry in the game
  (`carry 35`); *no* `int` or `agi` upside. vs. **dwarf**: dwarf is far tankier
  (`armor +2`, carry 100) and its `wis +1` is a side dish to a str/armour tank kit,
  while gnome triples that wisdom and adds a `healing +2` dwarf lacks — dwarf wins
  durability, gnome wins support/healing, neither dominates. vs. **elf**: elf's
  `mana +10`/`agi +2`/carry 40 all beat gnome's smaller numbers and elf owns the arcane
  `int +2`, but elf's `healing -2` is the opposite sign of gnome's `+2` and elf has zero
  wisdom — elf stays the mage/necromancer pick, gnome the cleric/druid/shaman pick,
  neither package a superset. vs. **troll**: both tie on `healing +2`, but troll's
  positive `str +3`/carry 80 build it around melee attrition while gnome's `str -3` makes
  it the worst melee race in the game — same headline healing number, opposite archetype.

## Areas

Every room now belongs to exactly one formal `data/areas/` entry (#529), each with
hand-drawn ASCII map art, an obtainable map item, and atlas connections — all
enforced by `--validate-data`. The "Formal entry + map + atlas" column is therefore
a validator rule, not a manual checkbox.

| Area (formal id) | Formal entry + map + atlas | Quest coverage | Difficulty band documented |
|---|---|---|---|
| Greystone Town (`town`) | ✅ #529 | ✅ #518 (starter chain) | ✅ #550 |
| Darkwood Wilds (`darkwood`) | ✅ #529 | ✅ (rat/wolf/spider quests) | ✅ #550 |
| Broken Ruins (`ruins`) | ✅ #529 | ✅ (bandit quests) | ✅ #550 |
| Sunless Catacombs (`catacombs`) | ✅ #529 | ✅ (crypt/explore quests) | ✅ #550 |
| Undercity Sewers (`sewers`) | ✅ #529 | ✅ (plague-rat) | ✅ #550 |
| Frozen Peaks (`frozen-peaks`) | ✅ #529 | ✅ (frostbound-cull) | ✅ #550 |
| The Emberdeep (`emberdeep`) | ✅ #529 | ✅ (ember-culler, pyraxis) | ✅ #550 |
| Cinder Reaches (`cinder-reaches`) | ✅ #529 | ✅ (ember-culler, pyraxis) | ✅ #550 |
| Shrouded Isle (`shrouded-isle`) | ✅ #529 | ✅ (drowned-watch, tidebreaker) | ✅ #550 |
| The Voidscar (`voidscar`) | ✅ #559 | ✅ (voidscar-cull, voidscar-unlight #561) | ✅ #559 |
| The Marrow Bloom (`marrow-bloom`) | ✅ #579 | ✅ (marrow-cull, marrow-bloom #581) | ✅ #579 |
| The Bonelight Choir (`bonelight-choir`) | ✅ #608 | ✅ (bonelight-cull, bonelight-choir #609) | ✅ #608 |
| The Unsung (`the-unsung`) | ✅ #633 | ✅ (unsung-cull, unsung-last-stand #634) | ✅ #633 |
| The Undersong (`the-undersong`) | ✅ #655 | ✅ (undersong-cull, undersong-source #656) · signature boss loot: the First Hum drops the Underhum Knell + Standing Tone Torc #677 | ✅ #655 |
| The Interval (`the-interval`) | ✅ #671 | ✅ (interval-cull, interval-fermata #672) · signature boss loot: the Fermata drops the Caesura Stroke + Crown of the Held Beat #677 | ✅ #671 |
| The Coda (`the-coda`) | ✅ #697 | ✅ (coda-cull, coda-resolution #698) · signature boss loot: the Coda drops the Closing Chord + Signet of Final Resolution #703 | ✅ #697 (81-88) |
| The Encore (`the-encore`) | ✅ #727 | ✅ (encore-cull, encore-ovation #728) · signature boss loot: the Ovation drops the Final Encore + Medal of the Standing Ovation | ✅ #727 (89-96) |

## Systems

Engine-level features that shipped, with their known completion gaps.
A system with all-✅ rows carries no obligation; ❌/🔨 entries are what the
game-designer should propose closing.

| System | Shipped | Known gaps |
|---|---|---|
| Leveling & XP | ✅ | practice-point sink ✅ #522 · class gains ✅ #523 · combat scaling ✅ #524 |
| Combat core | ✅ | worded damage + condition display ✅ #525 · attributes ✅ #524 · elemental damage types + armour resistance ✅ #563 · deterministic Enchanting path to fire/cold/poison resistance (of-embers/of-rime/of-warding) ✅ #577 #619 · mob flee/self-preservation ✅ #567 · epic rarity tier for boss-exclusive signature loot ✅ #583 · PvE (mob) melee now shares hit/crit/block resolution with PvP ✅ #589 · ranged player attacks (SHOOT) now share hit/crit resolution — a shot can miss or crit ✅ #591 (mob special-ability attacks already covered by `resolveMobAttack`, no gap there) · AoE spell / summoned-pet-attack hit/crit resolution — a spell can miss/crit per target and a pet swing (and the foe's retaliation) can miss/crit ✅ #595 · mob pack-assist / group pulls — a `"pack"`-tagged mob joins a fight already begun against a room-mate (waking the den on a solo pull), making stealth pulls, crowd-control, and AoE tactically matter; never starts fresh aggro and still obeys stealth for its own first engagement ✅ #617 · agility-driven **parry + riposte** — a defender wielding a melee weapon in the mainhand (any two-hander, dual-wielder, or shieldless caster) may fully avoid an otherwise-landing melee hit and answer with a free counter-strike, in both PvP duels (`CombatEngine`) and a mob's melee swing (`MobRegistry.resolveMobAttack`); parry is checked ahead of, and mutually exclusive with, the shield block, scales AGI→0–25%, and persists no new Player state (computed live) ✅ #639 · mob-side parry vs. player melee — a defensively-trained mob (authored `parry_chance`, e.g. the Bandit Captain) can now fully parry an otherwise-landing player *melee* swing (both the initial `KILL`/`ATTACK` strike and the per-tick auto-attack), dealing 0 damage that swing and riposting the attacker with its own `attack_id`, symmetric to the player-side parry on the mob's swing; melee-only (ranged `SHOOT`, AoE-spell, and summoned-pet damage never roll it), clamped to `MAX_PARRY_CHANCE`, seeded RNG, and no `Player`/mob schema change (the new `parry_chance` field is optional and defaults to 0, so existing mob data is unaffected) ✅ #645 · single-target ability (CAST/USE) mob-targeting + hit/crit — `CAST <spell> <mob>` / `USE <skill> <mob>` now find and strike a monster in the room (every class's `HARMFUL`/`HARMFUL_OPENER`/`HARMFUL_UNDEAD` attack, e.g. `bash`/`fireball`/`backstab`) instead of failing "target not found", rolling hit/crit through the shared `resolveHit` used by melee/ranged/AoE and awarding full XP/gold/loot/reputation on a kill; `HARMFUL_UNDEAD` gates on the mob's `undead` tag and `HARMFUL_OPENER` keeps its stealth bonus + opener gate, while the same abilities aimed at another player (duels/PvP) are unchanged — no schema change (`MobRegistry.processPlayerSingleTargetAbility`) ✅ #651 · mob-side elemental resistance/vulnerability — closes the prior asymmetry where the elemental system (#563/#577/#619) only ever fired in the player-defends-against-mob and PvP directions: a spell's damaging `VITALS` effect can now carry an optional `damage_type` (`FIRE`/`COLD`/`POISON`; `spell.fireball(.greater)`→FIRE, `spell.frostbolt`→COLD, `skill.envenom`/`spell.plague-cloud`→POISON) and thematically-elemental mobs carry optional `resistances`/`vulnerabilities` maps (ice mobs `frost-wyrm`/`frost-giant`/`frost-imp`/`blizzard-wraith` resist cold 50% & are weak to fire 50%; fire mobs `cinder-tyrant`/`cinder-wraith`/`ember-wyrm` the reverse). A resisted typed hit is reduced (capped at `CombatSettings.maxResistancePercent()`, never fully negated) and a vulnerable one amplified (capped at `MAX_VULNERABILITY_PERCENT`=200), narrated with a legible strike qualifier; untyped/`PHYSICAL` damage (melee, ranged, untagged spells) is unaffected — additive `mob.v4` schema (defaults empty, existing data loads unchanged) & no `Player` schema change (`MobRegistry.applyMobElemental`) ✅ #675 · spell cast time & interruption — a curated set of marquee spells (`spell.plague-cloud`, `spell.hurricane`, `spell.holy-nova`, `spell.siren-song`, `spell.chain-heal`) are now **channeled**: casting one takes several ticks (`cast_time_ticks`), during which the caster is visibly casting ("You begin casting …"), cannot start another ability or FLEE, and any damage from any source (mob melee, PvP spell, or a damage-over-time effect — funneled through one shared HP-drop hook in `PlayerSession`) interrupts the cast, fizzling the spell with no mana spent (cost is deferred, never reserved) and applying a short interrupt cooldown; COOLDOWNS/EFFECTS surface the in-progress cast and ticks remaining. Cast state is session-transient (never persisted; dropped on death/logout/reconnect). Additive optional `cast_time_ticks` field on the ability schema (defaults to 0 = instant, so every existing ability is unchanged) & no `Player` schema change (`SpellCastState`, `PlayerTicker`). **Follow-up gap:** player-cast only — mob-side cast interruption (interrupting a monster's channeled ability) is explicitly out of scope ⚠️ ✅ #693 · **boss special-attack telegraph** — a mob's `special_attack_id` can now carry an optional `telegraph_ticks` (additive `attack.v7` schema; defaults 0 = today's instant fire, all pre-v7 attacks unchanged): instead of landing its signature special instantly on the first swing, the boss announces a `TELEGRAPH` message (self + room channel) and winds up for that many AI ticks, spending its turn channeling (no normal hit that tick — net damage over the fight is not increased) before the blow resolves through the same `resolveMobAttack` hit/crit/parry/block/on-hit pipeline. The pending attack is cancelled with no damage if the mob dies or the target flees/leaves/dies mid-wind-up, so a player can see it coming and FLEE, heal, or pop a defensive cooldown. Telegraph state is transient server-only `MobInstance` state (parallel to `specialAbilityUsed`); no `Player`/save change. Carried by the capstone bosses' signature specials (the Coda's Final Chord, the Fermata's Resolution, the First Hum's Underhum) ✅ #707 · **CONSIDER elemental affinity** — `CONSIDER`/`CON <mob>` now appends a second line whenever the target authors any `resistances`/`vulnerabilities` (e.g. "It looks resistant to cold and vulnerable to fire."), using the same player-facing damage-type words (`fire`/`cold`/`poison`) as combat's strike qualifiers, so a caster can scout the right spell *before* engaging — closing the discoverability gap called out for #675 (the resistance/vulnerability pair was previously learnable only by burning mana and watching the number shrink). Read-only, no schema or `Player` change (`SocketCommandContextImpl.elementalAffinityLine`) ✅ #717 · **crowd-control effects (root/silence/stun) are now mechanically enforced, not cosmetic** — the previously flavor-only control effects shipped across the veteran→mythic ability tiers now carry an additive `control` classification on the effect schema (`ROOT`/`SILENCE`/`STUN`, defaults to none so every existing effect loads unchanged) that gates real commands: a `ROOT` (`rooted`/`shackled`/`earthbound`/`pinned`) blocks `MOVE`/`GO`/`WALK` and `FLEE`; a `SILENCE` (`silenced`/`garrote`) blocks `CAST` of a spell-type ability but leaves skill `USE` free; a `STUN` (`hammer-of-justice`) blocks all four. A blocked command prints a refusal naming the offending effect and is a complete no-op (no move-point spend, no mana). The check is a pure read of transient per-session effect state via `EffectEngine.activeControl` shared by all three commands (auto-follow moves cancel the follow instead of teleporting the rooted follower); existing stat-modifier penalties and durations are untouched — the lock is purely additive, and there is no `Player`/save schema change ✅ #719 · **support-caster healer mobs** — a mob template can now carry an additive `healer` flag (plus `heal_min`/`heal_max` and an optional `heal_threshold`, default 50%; no `mob.v4` bump, existing data loads unchanged): on its own AI decision, before the normal attack branch, a healer heals the most-wounded different, alive, non-pet mob in its room that sits at or below the wounded threshold (and is not already full), restoring a seeded `heal_min`..`heal_max` HP clamped to the ally's max and announcing it to the room (`The <mob> chants over the <ally>'s wounds — it looks steadier!`) instead of attacking that decision; with no wounded ally it attacks normally via `attack_id`. It composes with the existing AI branches (pack-assist #617, faction aggro, flee #567, telegraph wind-up #707, stealth first-engagement) — a healer mid-telegraph or mid-flee does not also heal that tick — and `CONSIDER`/`CON <mob>` shows a discoverability hint (`It looks like it could heal its allies.`) mirroring the elemental-affinity hint (#717), turning multi-mob pulls into a "kill the healer first" target-priority puzzle. Shipped on the **Bandit Medic** reinforcing the Broken Ruins bandit camp (`crumbling-courtyard`). No `Player`/save schema change (`MobRegistry.tryMobHeal`, `HealerProfile`) ✅ #733 · **boss enrage timers** — a mob template can now carry an additive `enrage_ticks` (positive int) + `enrage_damage_multiplier` (`> 1.0`) pair (`mob.v4`, no version bump; both optional and defaulting to "never enrages", so existing data loads unchanged): once a fight against the mob runs past `enrage_ticks` committed AI attack decisions against an already-engaged target, the boss **enrages** exactly once — a room announcement (`The <mob>'s eyes blaze with fury — it grows enraged!`) fires and its landed melee/special damage is multiplied by `enrage_damage_multiplier` for the rest of that encounter (applied in `resolveMobAttack` alongside the shield-block/elemental mitigation, so a defender's gear still matters). It punishes pure attrition/turtling and rewards burst cooldowns on precisely the climactic fights, without touching ordinary trash. Enrage is transient, per-`MobInstance` encounter state (parallel to `specialAbilityUsed`/telegraph): it resets on full disengage/respawn so a fresh pull is never enraged, persists through the boss's telegraphed special, and telegraph wind-up ticks (#707) — plus turns spent fleeing (#567), healing (#733), or the first swing of a fresh pull / a pack-assist join (#617) — never advance the clock or enrage on the same tick. `CONSIDER`/`CON <mob>` shows a discoverability hint (`It looks like it will wear down slowly, then grow dangerous.`) mirroring the elemental (#717) and healer (#733) hints. PvP duels are entirely unaffected (mob-only). Shipped on the four capstone/world bosses that already telegraph a signature special: the **First Hum**, the **Fermata**, the **Coda**, and the **Ovation**. No `Player`/save schema change (`MobRegistry.announceEnrage`, `MobInstance.advanceEnrage`). **Follow-up gap:** currently authored only on those four bosses; rolling enrage out to other elite/long-form encounters is tracked in #746 ⚠️ ✅ #745 |
| Status effects visibility | ✅ #685 | `EFFECTS`/`AFFECTS` lists the player's own active status effects split into **Beneficial** and **Harmful** groups (classified from each effect's net stat-modifier direction — a positive `damage_per_tick` counts as harmful), each line showing display name, remaining duration in ticks (or `permanent`) and stack count when stacked; "You have no active effects." when none. `LOOK <player>` now also renders each visible effect's authored `examine`-phase flavour line (e.g. "Bob is blessed."), reusing `EffectEngine.examineLines`; effects without an `examine` message are silently skipped. Pure read of `Player.effects()`/`EffectRepository` — no mutation, no persistence/schema change (all 45 `effects/*.json` already carry an `examine` message, so none are intentionally left without one) |
| Ability cooldown visibility | ✅ #691 | `COOLDOWNS`/`CD` lists every ability in `player.getLearnedAbilities()` in an `ABILITIES`-style table (Ability / Type SKILL·SPELL / Status), where Status is **live** readiness read from the player's own `PlayerSession#getAbilityCooldowns()` (`CooldownSystem#isOnCooldown`/`remainingTicks`, keyed by `AbilityId#getValue()`): `Ready` when off cooldown, or `<n> ticks` remaining otherwise. Closes for ability readiness the same gap `EFFECTS` (#685) closed for status effects — before, the only way to learn an ability's current readiness was to try to use it and be told reactively ("Ability is on cooldown (N ticks remaining)"); `ABILITIES` only prints each ability's static *base* cooldown length. "You have not learned any abilities yet." when none, mirroring `ABILITIES`. `HELP COOLDOWNS`/`HELP CD` documents the columns. Pure per-session read of already-tracked transient cooldown state — no `Player`/save-schema change, nothing to migrate; readiness formatting extracted to `CooldownStatusFormatter` for network-free unit coverage. No known gaps |
| Itemization | ✅ | carry capacity is now itemized via the `back` equipment slot — worn packs (a worn satchel +10, a leather backpack +18, a reinforced rucksack +25) carry a `"carry"` stat that adds a flat bonus to max carry weight, stacking with race/class base and summed exactly like armour `"ac"` ✅ #605 · every zone-capstone world boss now drops signature boss-exclusive gear — extended past the Bonelight Cantor (Cantor's Knell + Cantor's Reliquary ✅ #627) up through the highest zones so the itemization curve is unbroken at the level cap: the Undersong's First Hum drops the Underhum Knell (epic two-handed BLUNT weapon) + Standing Tone Torc (rare neck accessory), and the Interval's Fermata drops the Caesura Stroke (epic two-handed BLUNT weapon) + Crown of the Held Beat (epic head piece) ✅ #677, and the top zone's Coda drops the Closing Chord (epic two-handed BLUNT weapon) + Signet of Final Resolution (epic finger accessory) ✅ #703 · bulk item handling — `GET ALL` (picks up every floor item, stopping and reporting when overburdened), `GET ALL FROM <container>`, and `DROP ALL` (drops every unequipped item, never auto-unequipping worn gear) layered on the single-item GET/DROP family, reporting one summarized line ✅ #641 · no known gaps |
| New-player funnel | ✅ #516 | starter weapon/hints ✅ #517 · starter quest ✅ #518 · starting resources ✅ #519 · death grace ✅ #520 · creation info ✅ #521 |
| Cartography | ✅ #529 (map items; MAP command retired) | difficulty bands ✅ #550 |
| Quests (kill/explore/delivery/daily) | ✅ | level hints ✅ #518 · independent story + daily quest slots — a player can hold one `QUEST` contract and one `DAILY_QUEST` at once; a kill progresses both slots independently; `DAILY_QUEST ABANDON` added ✅ #599 · daily pool coverage widened across the full 1-80 curve — `frontier-slayer` (~8-22), `warband-slayer` (~20-48) and `silence-slayer` (~49-80) pools added alongside `slayer` (1-10) ✅ #681 |
| Economy (shops, auction, bank, guild treasury, mail gold) | ✅ | newbie bootstrap ✅ #519; auction search/sort QoL ✅ #565 · personal vault upgrades: `VAULT UPGRADE` at a bank NPC pays escalating gold (5,000 / 15,000 / 40,000) to permanently raise the player's own vault cap in +10-slot tiers `30→40→50→60` (per-player persisted `vaultTier`, defaults to 0 for existing saves, never regresses); `VAULT` shows slots used vs. effective capacity and the next tier's cost/gain ✅ #647 · vendor/repair coverage now spans the full level curve, not just the level-1 town + Shrouded Isle: eight traveling quartermaster NPCs (combined shopkeeper + `blacksmith`, non-combatant `max_hp: 9999`/`xp_reward: 0`/`loot: []`) placed at each area's waypoint/entrance room across the whole 14-88 chain — Sigrun (Frozen Peaks 14-22, `frozen-peaks-foothills`), Vael (Voidscar 32-40, `voidscar-the-rift`), Myca (The Marrow Bloom 41-48, `marrow-bloom-spore-descent`), Ossa (Bonelight Choir 49-56, `bonelight-choir-descent`), Tacet (The Unsung 57-64, `unsung-threshold`), Threnody (Undersong 65-72, `undersong-descent`), Caesura (The Interval 73-80, `interval-threshold`), Fine (The Coda 81-88, `coda-threshold`) — each with a themed general-goods `data/shops/*.json` (potions/cure/scrolls/food/light) so `REPAIR`/`CRAFT`/`SALVAGE`/`BUY`/`SELL` all work mid-to-endgame without backtracking to town; pure `data/` content reusing the existing shop + blacksmith-tag room-scan ✅ #669, gap zones filled ✅ #689, level-cap Coda zone covered ✅ #725 · auction watch alerts: `AUCTION WATCH <keyword>` adds a normalized keyword to a per-player watch list (capped at 10, persisted additive field `auctionWatches`, defaults empty for existing saves — no migration), `AUCTION UNWATCH`/`AUCTION WATCHLIST` manage it, and a successful `AUCTION SELL` notifies every online non-seller player whose watch list matches the listed item name in real time via `MessageBroadcaster#sendToPlayer`, reusing `AuctionFilter` keyword semantics so watch and `AUCTION LIST <keyword>` never disagree (online-only, no offline backfill, no new tick-loop I/O) ✅ #711 · bulk-sell QoL: `SELL ALL` sells the player's whole unequipped inventory to the room's shop in one command at the shop's existing `sellRatio()`/`adjustedSellValue` pricing, folding into one atomic player update and a single summary line (item count + total gold), and `SELL ALL <keyword>` narrows the sweep to inventory items whose name contains the case-insensitive keyword (mirroring `GET ALL FROM`/`REPAIR ALL`); equipped gear is never touched and single-item `SELL <item>` is unchanged ✅ #739 |
| Professions (craft/cook/brew/gather/salvage/enchant/tan/cut/sew) | ✅ | new `min_skill: 4` blacksmithing tier (Voidglass Focus, from voidglass-shard) gives profession proficiency somewhere past level 3 to grind toward · new Enchanting affixes of-warding (poison_resist, from blightspore-cluster) and of-resonance (wisdom/mana, from resonant-bonelight-shard) turn three orphaned zone drops into crafting sinks ✅ #619 · new Leatherworking profession (`TAN`, leatherworker NPC Della in Darkwood's tangled undergrowth) turns beast pelts/fangs/troll-teeth into light armor for agility/caster classes — five recipes across chest/legs/hands/feet/head with a `min_skill: 4` Direhide Cowl payoff tier, tracked via the generic string-keyed `PlayerProficiencies` map (no save migration) ✅ #629 · `SCORE` now lists every practised profession (level + progress to next) via `ProfessionId.known()` instead of a hardcoded three, so Enchanting/Leatherworking proficiency is visible in-game ✅ #637 · new Jewelcrafting profession (`CUT`, jeweler NPC Lapida in Broken Ruins' crumbling courtyard) cuts rough-quartz/raw-garnet (gathered from new GATHER veins in the Collapsed Tower and Sunless Catacombs' burial alcove) into caster-lane `finger`/`neck` accessories — five recipes (Quartz Band/Pendant, Garnet Ring/Choker) with a `min_skill: 4` Starcut Heartgem payoff, closing the last two profession-crafted equipment slots, same generic `PlayerProficiencies` map (no save migration) ✅ #667 · new Tailoring profession (`SEW`, tailor NPC Wefa in Darkwood's muddy hollow) sews spider-silk (a new `giant-spider` loot drop) and grave-linen (gathered from a new GATHER node in the Sunless Catacombs' crypt corridor) into the first caster-lane `chest`/`legs`/`hands`/`feet`/`head` cloth armor (`int`/`wis`/`mana`) — five recipes (Silkweave Gloves/Slippers/Robe, Gravebound Leggings) with a `min_skill: 4` Shroudweave Hood payoff, the mirror of Jewelcrafting's finger/neck gap on the five armor slots, same generic `PlayerProficiencies` map (no save migration) ✅ #683 · Alchemy (`BREW`) and Cooking (`COOK`) — the last two professions whose recipes capped at `min_skill: 2` — gain a `min_skill: 3` and `min_skill: 4` tier each, so all seven professions now share a genuine 1→4 grind curve: Alchemy adds a Greater Vigor Elixir (skill 3, bigger hp/mana restore than elixir-of-vigor) and a Greater Antivenom (skill 4, cures poison *and* heals, built around higher-zone `blightspore-cluster`); Cooking adds a Ferryhand's Feast (skill 3, new `hearty-feast` +3/+3 buff on Shrouded Isle deckside-mackerel) and a Grand Isle Feast (skill 4, new stronger `grand-feast` +5/+5 buff on higher-zone fogfin-cod), each a real upgrade over hearty-stew/chowder's well-fed ✅ #695 |
| Social (friends, tells, gossip, ignore, LFG, boards, guilds, parties) | ✅ | party loot modes: free · round-robin · roll (highest 1-100 roll wins each drop, ties re-roll) ✅ #593 · guild levels (1-5): lifetime `GUILD DEPOSIT` total (withdrawals never reduce it) drives a level on fixed thresholds `0/500/2,000/5,000/15,000` that scales the shared vault cap `40/50/60/70/80` slots; `GUILD`/`GUILD VAULT` show level + progress, `GUILD DEPOSIT` announces level-ups ✅ #625 · player marriage: `MARRY <player>` proposes in-room (60s window, one pending proposal per player, mirrors DUEL/TRADE propose-accept), `MARRY ACCEPT`/`DECLINE` responds, accept bonds both players and fires a server-wide wedding announcement (flavor only, no stat/gold/combat reward), `MARRY DIVORCE` unilaterally ends the bond (online spouse notified immediately, offline spouse cleared + mailed), `MARRY STATUS` shows spouse/pending state, `SPOUSETELL`/`MARRY TELL <msg>` (alias `ST`) privately messages the spouse anywhere in the world exempt from `IGNORE`; spouse shown in `WHO`/`SCORE`; nullable `spouse` on `Player` persists across logout/restart (defaults unmarried for existing saves, no migration); purged/deleted spouse self-heals to single on next `MARRY STATUS`/login ✅ #649 · party chat: `PTELL <message>` (and free text after `PARTY`, mirroring `GUILD`/`GC`) sends `[Party] <Sender>: <msg>` to every online party member in any room and echoes `[Party] You: <msg>` to the sender; online-only (no mailbox/queue), rejects with `You are not in a party.` when solo and `Say what to your party?` when blank; read-only over `PartyService` (no `Player`/`Party` schema change) ✅ #663 · cooperative guild quests: every guild holds one shared `slay N <mob type>` objective at a time (`GUILD_QUEST`/`GQUEST`/`GUILD QUEST` shows objective + `Slayed X / Y <mob>` progress + treasury reward), assigned from a level-banded JSON pool (`data/quests/guild/`, 8 objectives spanning guild levels 1-5) and re-rolled daily via `GuildQuestRotationTicker` (same NIGHT→DAY cadence as daily quests); any online member's kill of the matching mob anywhere credits the whole guild automatically (no ACCEPT — parallel to `QuestKillService`, never touches personal quest slots), counter clamps at target; on completion the reward gold is paid straight into the guild treasury (counts toward `lifetimeDepositedGold`/`GuildLevel`), a `[Guild]` announcement fires and a fresh objective rolls; per-guild quest state persists on `Guild` (guild schema v4, additive — pre-v4 guilds lazily assigned on first access, no migration) ✅ #687 · guild leaderboard: `RANK GUILDS` (a sub-command of `RANK`, alongside `RANK`/`RANK DUELS`) lists every persisted guild server-wide via `GuildRepository#loadAll()` (read-only, reader thread — never the tick thread, mirrors `RANK`/`RANK DUELS` over players), ranked by `GuildLevel` descending and tiebroken by `lifetimeDepositedGold` descending; each row shows rank #, guild name, level (1-5), member count, and leader username; a friendly `No guilds have been founded yet.` message replaces an empty table; no schema change ✅ #705 · party gold split: a slain mob's gold drop is now divided evenly across the same eligible (alive, in-room) party members as the XP split — rolled once via `GoldDrop#roll`, then floor-divided by `partyRecipients.size()` (dead members present count toward the divisor but receive nothing, so the total paid never exceeds the roll and any remainder is dropped); each recipient's gold message names their own share (`The Goblin drops 12 gold coins. You receive 4.`) while a solo kill keeps the legacy flat message and full roll unchanged (no schema change — gold is a plain `int`) ✅ #713 · guild wars: direct, consensual guild-vs-guild competition — a guild leader declares `GUILD WAR <guild>` on a rival (mirrors the MARRY/TRADE/DUEL propose-accept UX: the target guild's leader has 60s to `GUILD WAR ACCEPT`/`DECLINE`, leader-only on both sides, one active war per guild on either side); while a war runs, any consensual `DUEL` that resolves between live members of the two warring guilds (membership re-checked at resolution, not snapshotted — an ex-member no longer counts) awards one war point to the winner's guild on top of the duel's normal outcome (rankings/`DUEL WAGER` gold unchanged), while same-guild/unguilded/non-warring duels never score; first guild to 5 war points auto-wins (server-wide announcement, persistent `warWins++`, war ends), `GUILD WAR CONCEDE` forfeits early crediting the rival, `GUILD WAR STATUS` shows opponent + live score + target; `RANK GUILDS` gains a war-wins column (tiebreaks after lifetime gold) and `HELP GUILD WAR` documents it; hooked at the single `GameActionService#endPlayerDuel` choke point via `GuildWarService`, war state persists on `Guild` (guild schema v5, additive nullable `active_war` + `war_wins` default 0 — pre-v5 saves load unchanged, no migration) ✅ #731 |
| Identity/RP (titles, custom LOOK description, named companions) | ✅ #569 | companion naming `NAME <pet>` ✅ #571 · companion descriptions `DESCRIBE <pet> [text\|CLEAR]` ✅ #623 (owner-set roleplay text shown to everyone who `LOOK`s at the pet; 240-char cap; persists across logout/re-tame like the custom name; owner-only `DESCRIBE <pet>` query shows a "none set" hint, `LOOK` never does) |
| Player preferences (`ANSI`, `AUTOLOOT`, `PROMPT`, `BRIEF`, `AUTOASSIST`) | ✅ | brief-mode room descriptions `BRIEF [on\|off\|toggle\|status]` ✅ #575 (movement skips prose; `LOOK` always shows it) · auto-assist `AUTOASSIST [on\|off\|toggle\|status]` ✅ #709 (opt-in, default OFF; when a party-mate lands their opening attack on a *fresh* mob, every enabled member in the same room who is not already in combat, resting, or dead is auto-joined to the fight as if they typed `ASSIST <attacker>`, with a per-member notice; fires once per new engagement, never on pack-assist joins or subsequent swings; manual `ASSIST` still works regardless; additive `autoAssistEnabled` boolean on `Player`, defaults `false` for existing saves, no migration) |
| PvP (duels, rankings, arena events) | ✅ | opt-in gold **wager duels** — `DUEL WAGER <player> <gold>` stakes a positive whole number of gold: the challenger must hold it to challenge, the target's live gold is re-checked at `ACCEPT` (duel is cancelled with no dangling state and no gold moved on failure), and on resolution the stake transfers loser→winner as a single atomic transfer clamped to the loser's live gold in `GameActionService#endPlayerDuel`; no escrow object exists, so forfeits/disconnects/challenge timeouts never move a coin, and plain `DUEL <player>` stays byte-for-byte free and risk-free; wager is transient in-memory-only state on `PlayerDuelState` (no `Player` schema change) ✅ #661 |
| World events (timed rare-elite spawns) | ✅ #585 | none known |
| Pets (tame/summon) | ✅ | custom companion names ✅ #571 · custom companion descriptions ✅ #623 · owner-level companion scaling — a tamed or summoned companion's effective max HP and dealt damage now scale with the owner's level at spawn time via a single shared, deterministic, capped `CompanionScaling` formula (TAME and SUMMON alike), so a high-level owner's pet is tougher and hits harder than the same template obtained early; recomputed only on a fresh tame/summon or a login respawn (no mid-session growth), no `Player`/`MobInstance` schema change or save migration ✅ #653 (corrects the prior stale "no known gaps" note) |
| Mounts (`MOUNT`/`DISMOUNT`, per-step move-point discount) | ✅ #494 #495 | tiered stable stock: sturdy-pony (150g, -1) · swift-warhorse (600g, -2) · militia war-charger (2,500g, -3, `min_reputation: 20` on the now-`faction_id: militia` stable, mirroring the armory's capstone gates) — a reputation-gated aspirational mount for out-levelled players ✅ #631 |
| Travel / recall (`RECALL`, death respawn, `BIND`, `WAYFIND`, `CORPSE`) | ✅ | movable recall anchor: `BIND` (no args) reports the current recall/respawn point, `BIND HERE` anchors it to the waypoint (entrance room, `room_ids[0]`) of the player's current area when standing in it and out of combat (mirrors `RECALL`'s duel/combat lockout, no cooldown); `RECALL` and death respawn (`PlayerRespawnTicker`) then return the player to the bound waypoint instead of the hardcoded Greystone Town, falling back to the default when unbound or when the stored room no longer resolves; re-`BIND` at a later zone moves the anchor forward; nullable `boundRoomId` on `Player` persists across logout/restart (defaults unset for existing saves = today's respawn-to-town behavior, no migration) ✅ #659. `WAYFIND <area>` prints live turn-by-turn compass directions (BFS over the visible room graph via `RoomPathfinder`/`WayfindService` — regular exits plus globally-discovered hidden exits; locked doors kept in the route, undiscovered secret exits excluded) to the destination area's waypoint plus the step count; `WAYFIND` with no args lists the current area and its atlas neighbours; case-insensitive name/id resolution with partial-match suggestions and an ambiguity prompt; read-only, no save schema change ✅ #721. Ferry-aware routing: when the shortest (or only) way across needs the Coastal Ferry, the route splices in a distinct "board the <ferry> at <dock> and ride to <dock>" step between the walk to the boarding dock and the walk from the arrival dock (walk-only routes render byte-for-byte unchanged; shortest total-step route wins, deterministic ties); scoped to at most one ferry leg (the only ferry today is a two-dock route) ✅ #723. Corpse recovery: `CORPSE` (no args) reports the player's single tracked corpse — "no corpse in the world" when none/decayed/looted/resurrection-consumed, "standing on it" (with carried gold) when it is in the current room, or otherwise the room name, carried gold, a friendly decay-time-remaining estimate, and turn-by-turn directions back via the same ferry-aware `WayfindService.describeRoute` routing (or "no known route" when unreachable); read-only over `RoomItemService`'s transient corpse state, no new persisted state, no save schema change ✅ #735. Multi-corpse recovery: when a player has died more than once, bare `CORPSE` reports the corpse closest to decaying and appends a count line pointing at `CORPSE ALL`; `CORPSE ALL` lists every outstanding corpse numbered soonest-to-decay first (room, gold, decay-time-remaining, no per-row directions); `CORPSE <n>` gives the full report and ferry-aware directions for that numbered corpse (out-of-range index yields a clear error); `RoomItemService.findCorpsesByOwner`/`RoomService.findCorpsesByOwner` back the plural lookup while `RESURRECT` keeps targeting the single most-recent corpse; still read-only, corpses remain transient/server-only, no save schema change ✅ #741 |
| Weather & world ambience | ✅ | none known |
| Transports (telnet, SSH, WebSocket, browser web client) | ✅ #526 #527 | none known |
| Admin/wizard tooling | ✅ | none known |
| Classes (kits, level gains, attributes, descriptions, HELP) | ✅ #516 #521 #522 #523 #524 #547 | balance pass ✅ #556 |
| Exploration (locked doors, hidden exits + SEARCH) | ✅ #587 #612 #621 | discovery persists across restarts (#612); rogues get a levelled SEARCH find-chance bonus (#621) on top of the flat 50% base, capped below certainty and mirroring the PICK formula shape |
| Achievements (milestones: level, kills, quests) | ✅ | fixed unreachable quests_50 (36 one-time quests exist, threshold was 50 → retired for reachable quests_30) · extended level ceiling past 50 to match the level-96 curve (added level_75 mythic tier #715, level_96 The Encore ceiling #727) · added a kill milestone past 1000 (kills_5000) · content test guards that no `quests_completed` threshold outgrows the shipped one-time quest count ✅ #737 · every milestone now grants a unique `title_reward` on unlock (granted in `AchievementService#checkAndUnlock`, surfaced via `TITLE`; ACHIEVEMENTS shows a per-row `[Title: … - earned/locked]` marker), content test guards title uniqueness vs. other achievements and all quest titles ✅ #743 |
