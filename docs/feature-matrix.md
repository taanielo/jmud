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
| adventurer | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| bard | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| cleric | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| druid | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| mage | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| necromancer | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| paladin | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| ranger | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| rogue | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| shaman | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |
| warrior | ✅ (#516) | ✅ #522 | ✅ #523 | ✅ #524 | ✅ #521 | ✅ #547 | ❌ |

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

## Systems

Engine-level features that shipped, with their known completion gaps.
A system with all-✅ rows carries no obligation; ❌/🔨 entries are what the
game-designer should propose closing.

| System | Shipped | Known gaps |
|---|---|---|
| Leveling & XP | ✅ | practice-point sink 🔨 #522 · class gains ✅ #523 · combat scaling ✅ #524 |
| Combat core | ✅ | worded damage + condition display 🔨 #525 · attributes ✅ #524 |
| New-player funnel | ✅ #516 | starter weapon/hints ✅ #517 · starter quest ✅ #518 · starting resources ✅ #519 · death grace ✅ #520 · creation info ✅ #521 |
| Cartography | ✅ #529 (map items; MAP command retired) | difficulty bands ✅ #550 |
| Quests (kill/explore/delivery/daily) | ✅ | level hints ✅ #518 |
| Economy (shops, auction, bank, guild treasury, mail gold) | ✅ | newbie bootstrap ✅ #519 |
| Professions (craft/cook/brew/gather/salvage/enchant) | ✅ | none known |
| Social (friends, tells, gossip, ignore, LFG, boards, guilds, parties) | ✅ | none known |
| PvP (duels, rankings, arena events) | ✅ | none known |
| Pets (tame/summon) | ✅ | none known |
| Weather & world ambience | ✅ | none known |
| Transports (telnet, SSH, WebSocket, browser web client) | ✅ #526 #527 | none known |
| Admin/wizard tooling | ✅ | none known |
