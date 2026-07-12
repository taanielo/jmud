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

_Last full audit: 2026-07-12 (seeded by hand)._

## Classes

| Class | L1 offense | Trainable pool | Level gains | Attributes | Creation description | HELP entry | Balance pass |
|---|---|---|---|---|---|---|---|
| adventurer | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| bard | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| cleric | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| druid | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| mage | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| necromancer | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| paladin | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| ranger | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| rogue | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| shaman | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |
| warrior | ✅ (#516) | 🔨 #522 | 🔨 #523 | 🔨 #524 | 🔨 #521 | ❌ | ❌ |

## Races

| Race | Stat profile | Creation description | Attribute bonuses | Balance pass |
|---|---|---|---|---|
| dwarf | ✅ | 🔨 #521 | 🔨 #524 | ❌ |
| elf | ✅ | 🔨 #521 | 🔨 #524 | ❌ |
| human | ✅ | 🔨 #521 | 🔨 #524 | ❌ |
| orc | ✅ | 🔨 #521 | 🔨 #524 | ❌ |
| troll | ✅ | 🔨 #521 | 🔨 #524 | ❌ |

## Areas

Areas are informal until #529 lands (room-id prefixes only). Rows below use the
implicit groupings; #529's formal `data/areas/` entries replace this table's
first three columns with validator rules.

| Area (implicit) | Formal entry + map + atlas | Quest coverage | Difficulty band documented |
|---|---|---|---|
| Town/Keep (training-yard, armory, courtyard, …) | 🔨 #529 | 🔨 #518 (starter chain) | ❌ |
| Wilderness ring (muddy-hollow, hunters-clearing, forest, …) | 🔨 #529 | ✅ (rat/wolf/spider quests) | ❌ |
| Sewers | 🔨 #529 | ✅ (plague-rat) | ❌ |
| Catacombs | 🔨 #529 | ✅ (crypt/explore quests) | ❌ |
| Arena | 🔨 #529 | — (event area) | ❌ |
| Frozen Peaks | 🔨 #529 | ✅ (frostbound-cull) | ❌ |
| Cinder Reaches | 🔨 #529 | ✅ (ember-culler, pyraxis) | ❌ |
| Shrouded Isle | 🔨 #529 | ✅ (drowned-watch, tidebreaker) | ❌ |

## Systems

Engine-level features that shipped, with their known completion gaps.
A system with all-✅ rows carries no obligation; ❌/🔨 entries are what the
game-designer should propose closing.

| System | Shipped | Known gaps |
|---|---|---|
| Leveling & XP | ✅ | practice-point sink 🔨 #522 · class gains 🔨 #523 · combat scaling 🔨 #524 |
| Combat core | ✅ | worded damage + condition display 🔨 #525 · attributes 🔨 #524 |
| New-player funnel | ✅ #516 | starter weapon/hints 🔨 #517 · starter quest 🔨 #518 · starting resources 🔨 #519 · death grace 🔨 #520 · creation info 🔨 #521 |
| Cartography | ✅ (MAP, retiring) | area maps as items 🔨 #529 |
| Quests (kill/explore/delivery/daily) | ✅ | level hints 🔨 #518 |
| Economy (shops, auction, bank, guild treasury, mail gold) | ✅ | newbie bootstrap 🔨 #519 |
| Professions (craft/cook/brew/gather/salvage/enchant) | ✅ | none known |
| Social (friends, tells, gossip, ignore, LFG, boards, guilds, parties) | ✅ | none known |
| PvP (duels, rankings, arena events) | ✅ | none known |
| Pets (tame/summon) | ✅ | none known |
| Weather & world ambience | ✅ | none known |
| Transports (telnet, SSH) | ✅ | WebSocket 🔨 #526 · web client 🔨 #527 |
| Admin/wizard tooling | ✅ | none known |
