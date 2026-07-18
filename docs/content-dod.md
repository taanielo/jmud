# Content Definition of Done (DoD)

What "complete" means for each type of game content. The game-designer agent audits
shipped content against these checklists before proposing new features
(completion beats novelty), and any issue that introduces a **new content type**
must add a DoD section here in the same PR.

Two enforcement tiers per item:

- **[V]** — machine-checkable from `data/`; belongs in `--validate-data`
  (see the validator-completeness issue). Once implemented, incomplete content
  fails CI and needs no manual tracking.
- **[M]** — needs judgment (balance, prose quality, coverage); tracked per entity
  in [`feature-matrix.md`](feature-matrix.md).

## Race

- [V] Stat profile (carry/healing/mana/attack modifiers)
- [V] Description with benefits and features, shown at creation (#521)
- [V] Attribute bonuses once core attributes land (#524)
- [M] Balance pass vs. other races (no strictly-dominant pick)

## Class

- [V] Starting ability kit, including at least one level-1 offense usable on
  non-undead mobs (#516)
- [V] Trainable ability pool at levels 2–5, ≥2 abilities beyond the starting kit (#522)
- [V] Per-class level gains (HP/mana/move) (#523)
- [V] Attribute creation bonuses + level-gain schedule (#524)
- [V] Description with playstyle, benefits, and starting abilities, shown at creation (#521)
- [M] HELP entry naming the class's abilities and role
- [M] Balance pass: comparable total power at equal level

## Ability (skill/spell)

- [V] Definition in `data/skills/` with level, cost, cooldown, targeting, messages
- [V] Reachable by at least one class (starting kit or trainable pool) — **live** in `--validate-data` (#530)
- [M] Combat/utility niche that doesn't duplicate an existing ability
- [M] Worded-damage message compliance once #525 lands

## Mob

- [V] Explicit `xp_reward` (no silent max-HP fallback) — **live** in `--validate-data` (#530): a mob
  data file without `xp_reward` fails to load with an actionable message
- [V] Attack definition (or explicit `attack_id: null` for non-combatants) and loot/gold table —
  **live** in `--validate-data` (#530): a non-null `attack_id` and every loot `item_id` must resolve
- [V] Assigned spawn room that exists — **live** in `--validate-data` (#530)
- [M] Sits on the area's difficulty curve (CONSIDER gives sane advice)
- [M] Elemental identity (if any) is a believable resistance/vulnerability pair, not a strict upgrade
  (an ice mob resists cold and is weak to fire, never resists everything) — judgment-based, tracked per
  mob in the matrix rather than `--validate-data`

## Area / zone

- [V] Formal area entry (`data/areas/`) listing its rooms; every room in exactly one area (#529)
- [V] Hand-drawn ASCII map art + an obtainable map item (shop stock or loot) (#529)
- [V] Atlas connection entries realized by actual room exits (#529)
- [V] All area mobs meet the Mob DoD
- [M] At least one quest that targets or traverses the area
- [M] Difficulty band documented (level range) and consistent with mob stats
- [M] Loot/economy integration (shops, gatherables, or drop tables as fits the theme)

## Item

- [V] Schema-valid definition with weight, value, and phase messages where applicable
- [V] Obtainable somewhere (shop, loot, quest reward, craft recipe, gathering yield, salvage,
  newbie kit, or room placement) — no orphan items — **live** in `--validate-data` (#530)
- [M] Niche vs. existing items (COMPARE shouldn't reveal a strict duplicate)

## Quest

- [V] Valid target/room references; rewards defined — **live** in `--validate-data` (#530): target,
  giver, receiver mobs, receiver/required rooms, reward items and factions, and quest prerequisites
  must all resolve
- [V] Reachable giver (room or NPC that exists) — **live** in `--validate-data` (#530)
- [M] Recommended level set and honest (#518)
- [M] Fits a quest chain or standalone arc; no dead-end prerequisites

## System / mechanic (new engine-level feature)

- [M] Player-facing HELP entry
- [M] Smoke-test phase or unit coverage proving the happy path
- [M] Interactions with existing systems reviewed (combat, economy, death, persistence)
- [M] Save-game compatibility statement in the PR
- [M] Row added to `feature-matrix.md` with any known follow-up gaps filed as issues

## Achievement (milestone definition)

- [M] Player-facing HELP entry (`HELP ACHIEVEMENTS`) still accurately describes the command
- [M] Unit/smoke coverage proving the happy path: new definitions load, sort, and render via `AchievementService`/`AchievementsCommand`
- [M] Interactions reviewed: the milestone fires from the existing `AchievementService#checkAndUnlock` call sites (level-up, mob-kill, quest-completion) with no new hook
- [M] Every threshold is reachable given the content backing its condition (a `quests_completed` threshold must not exceed the one-time quest count; a `level` threshold must not exceed the level cap) — guarded by a content test, not honour-system
- [M] Save-game compatibility statement in the PR (new definitions are additive; no `Player`/save schema change; already-unlocked achievement IDs are unaffected)
- [M] Row updated in `feature-matrix.md`
