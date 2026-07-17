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

## Races

| Race | Stat profile | Creation description | Attribute bonuses | Balance pass |
|---|---|---|---|---|
| dwarf | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |
| elf | ✅ | ✅ #521 | ✅ #524 | ✅ #554 |
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

## Systems

Engine-level features that shipped, with their known completion gaps.
A system with all-✅ rows carries no obligation; ❌/🔨 entries are what the
game-designer should propose closing.

| System | Shipped | Known gaps |
|---|---|---|
| Leveling & XP | ✅ | practice-point sink ✅ #522 · class gains ✅ #523 · combat scaling ✅ #524 |
| Combat core | ✅ | worded damage + condition display ✅ #525 · attributes ✅ #524 · elemental damage types + armour resistance ✅ #563 · deterministic Enchanting path to fire/cold/poison resistance (of-embers/of-rime/of-warding) ✅ #577 #619 · mob flee/self-preservation ✅ #567 · epic rarity tier for boss-exclusive signature loot ✅ #583 · PvE (mob) melee now shares hit/crit/block resolution with PvP ✅ #589 · ranged player attacks (SHOOT) now share hit/crit resolution — a shot can miss or crit ✅ #591 (mob special-ability attacks already covered by `resolveMobAttack`, no gap there) · AoE spell / summoned-pet-attack hit/crit resolution — a spell can miss/crit per target and a pet swing (and the foe's retaliation) can miss/crit ✅ #595 · mob pack-assist / group pulls — a `"pack"`-tagged mob joins a fight already begun against a room-mate (waking the den on a solo pull), making stealth pulls, crowd-control, and AoE tactically matter; never starts fresh aggro and still obeys stealth for its own first engagement ✅ #617 · agility-driven **parry + riposte** — a defender wielding a melee weapon in the mainhand (any two-hander, dual-wielder, or shieldless caster) may fully avoid an otherwise-landing melee hit and answer with a free counter-strike, in both PvP duels (`CombatEngine`) and a mob's melee swing (`MobRegistry.resolveMobAttack`); parry is checked ahead of, and mutually exclusive with, the shield block, scales AGI→0–25%, and persists no new Player state (computed live) ✅ #639 — **known follow-up gap: mob parry vs. player attacks is out of scope in v1 (a mob's swing can be parried by the player, but a player's swing is never parried by the mob)** |
| Itemization | ✅ | carry capacity is now itemized via the `back` equipment slot — worn packs (a worn satchel +10, a leather backpack +18, a reinforced rucksack +25) carry a `"carry"` stat that adds a flat bonus to max carry weight, stacking with race/class base and summed exactly like armour `"ac"` ✅ #605 · every zone-capstone world boss now drops signature boss-exclusive gear — the highest-level Bonelight Cantor completes the set with the Cantor's Knell (epic two-handed BLUNT weapon) + the Cantor's Reliquary (rare neck accessory) ✅ #627 · bulk item handling — `GET ALL` (picks up every floor item, stopping and reporting when overburdened), `GET ALL FROM <container>`, and `DROP ALL` (drops every unequipped item, never auto-unequipping worn gear) layered on the single-item GET/DROP family, reporting one summarized line ✅ #641 · no known gaps |
| New-player funnel | ✅ #516 | starter weapon/hints ✅ #517 · starter quest ✅ #518 · starting resources ✅ #519 · death grace ✅ #520 · creation info ✅ #521 |
| Cartography | ✅ #529 (map items; MAP command retired) | difficulty bands ✅ #550 |
| Quests (kill/explore/delivery/daily) | ✅ | level hints ✅ #518 · independent story + daily quest slots — a player can hold one `QUEST` contract and one `DAILY_QUEST` at once; a kill progresses both slots independently; `DAILY_QUEST ABANDON` added ✅ #599 |
| Economy (shops, auction, bank, guild treasury, mail gold) | ✅ | newbie bootstrap ✅ #519; auction search/sort QoL ✅ #565 |
| Professions (craft/cook/brew/gather/salvage/enchant/tan) | ✅ | new `min_skill: 4` blacksmithing tier (Voidglass Focus, from voidglass-shard) gives profession proficiency somewhere past level 3 to grind toward · new Enchanting affixes of-warding (poison_resist, from blightspore-cluster) and of-resonance (wisdom/mana, from resonant-bonelight-shard) turn three orphaned zone drops into crafting sinks ✅ #619 · new Leatherworking profession (`TAN`, leatherworker NPC Della in Darkwood's tangled undergrowth) turns beast pelts/fangs/troll-teeth into light armor for agility/caster classes — five recipes across chest/legs/hands/feet/head with a `min_skill: 4` Direhide Cowl payoff tier, tracked via the generic string-keyed `PlayerProficiencies` map (no save migration) ✅ #629 · `SCORE` now lists every practised profession (level + progress to next) via `ProfessionId.known()` instead of a hardcoded three, so Enchanting/Leatherworking proficiency is visible in-game ✅ #637 |
| Social (friends, tells, gossip, ignore, LFG, boards, guilds, parties) | ✅ | party loot modes: free · round-robin · roll (highest 1-100 roll wins each drop, ties re-roll) ✅ #593 · guild levels (1-5): lifetime `GUILD DEPOSIT` total (withdrawals never reduce it) drives a level on fixed thresholds `0/500/2,000/5,000/15,000` that scales the shared vault cap `40/50/60/70/80` slots; `GUILD`/`GUILD VAULT` show level + progress, `GUILD DEPOSIT` announces level-ups ✅ #625 |
| Identity/RP (titles, custom LOOK description, named companions) | ✅ #569 | companion naming `NAME <pet>` ✅ #571 · companion descriptions `DESCRIBE <pet> [text\|CLEAR]` ✅ #623 (owner-set roleplay text shown to everyone who `LOOK`s at the pet; 240-char cap; persists across logout/re-tame like the custom name; owner-only `DESCRIBE <pet>` query shows a "none set" hint, `LOOK` never does) |
| Player preferences (`ANSI`, `AUTOLOOT`, `PROMPT`, `BRIEF`) | ✅ | brief-mode room descriptions `BRIEF [on\|off\|toggle\|status]` ✅ #575 (movement skips prose; `LOOK` always shows it) |
| PvP (duels, rankings, arena events) | ✅ | none known |
| World events (timed rare-elite spawns) | ✅ #585 | none known |
| Pets (tame/summon) | ✅ | custom companion names ✅ #571 · custom companion descriptions ✅ #623 |
| Mounts (`MOUNT`/`DISMOUNT`, per-step move-point discount) | ✅ #494 #495 | tiered stable stock: sturdy-pony (150g, -1) · swift-warhorse (600g, -2) · militia war-charger (2,500g, -3, `min_reputation: 20` on the now-`faction_id: militia` stable, mirroring the armory's capstone gates) — a reputation-gated aspirational mount for out-levelled players ✅ #631 |
| Weather & world ambience | ✅ | none known |
| Transports (telnet, SSH, WebSocket, browser web client) | ✅ #526 #527 | none known |
| Admin/wizard tooling | ✅ | none known |
| Classes (kits, level gains, attributes, descriptions, HELP) | ✅ #516 #521 #522 #523 #524 #547 | balance pass ✅ #556 |
| Exploration (locked doors, hidden exits + SEARCH) | ✅ #587 #612 #621 | discovery persists across restarts (#612); rogues get a levelled SEARCH find-chance bonus (#621) on top of the flat 50% base, capped below certainty and mirroring the PICK formula shape |
