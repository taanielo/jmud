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

## Races

| Race | Stat profile | Creation description | Attribute bonuses | Balance pass |
|---|---|---|---|---|
| dwarf | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |
| elf | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |
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

## Systems

Engine-level features that shipped, with their known completion gaps.
A system with all-✅ rows carries no obligation; ❌/🔨 entries are what the
game-designer should propose closing.

| System | Shipped | Known gaps |
|---|---|---|
| Leveling & XP | ✅ | practice-point sink ✅ #522 · class gains ✅ #523 · combat scaling ✅ #524 |
| Combat core | ✅ | worded damage + condition display ✅ #525 · attributes ✅ #524 · elemental damage types + armour resistance ✅ #563 · deterministic Enchanting path to fire/cold resistance (of-embers/of-rime) ✅ #577 · mob flee/self-preservation ✅ #567 · epic rarity tier for boss-exclusive signature loot ✅ #583 · PvE (mob) melee now shares hit/crit/block resolution with PvP ✅ #589 (remaining gap: ranged/special/AoE/pet attacks still use flat damage) |
| New-player funnel | ✅ #516 | starter weapon/hints ✅ #517 · starter quest ✅ #518 · starting resources ✅ #519 · death grace ✅ #520 · creation info ✅ #521 |
| Cartography | ✅ #529 (map items; MAP command retired) | difficulty bands ✅ #550 |
| Quests (kill/explore/delivery/daily) | ✅ | level hints ✅ #518 |
| Economy (shops, auction, bank, guild treasury, mail gold) | ✅ | newbie bootstrap ✅ #519; auction search/sort QoL ✅ #565 |
| Professions (craft/cook/brew/gather/salvage/enchant) | ✅ | none known |
| Social (friends, tells, gossip, ignore, LFG, boards, guilds, parties) | ✅ | none known |
| Identity/RP (titles, custom LOOK description, named companions) | ✅ #569 | companion naming `NAME <pet>` ✅ #571 (unblocks a future `DESCRIBE <pet>` issue) |
| Player preferences (`ANSI`, `AUTOLOOT`, `PROMPT`, `BRIEF`) | ✅ | brief-mode room descriptions `BRIEF [on\|off\|toggle\|status]` ✅ #575 (movement skips prose; `LOOK` always shows it) |
| PvP (duels, rankings, arena events) | ✅ | none known |
| World events (timed rare-elite spawns) | ✅ #585 | none known |
| Pets (tame/summon) | ✅ | custom companion names ✅ #571 |
| Weather & world ambience | ✅ | none known |
| Transports (telnet, SSH, WebSocket, browser web client) | ✅ #526 #527 | none known |
| Admin/wizard tooling | ✅ | none known |
| Classes (kits, level gains, attributes, descriptions, HELP) | ✅ #516 #521 #522 #523 #524 #547 | balance pass 🔨 #556 |
| Exploration (locked doors, hidden exits + SEARCH) | ✅ #587 | world-scoped discovery (in-memory, resets on restart); a rogue/skill-based bonus find chance is a possible future gap |
