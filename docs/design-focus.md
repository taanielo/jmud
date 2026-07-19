# Design Focus

The single multi-cycle **player-experience goal** the autonomous loop is working
toward right now. The [`feature-matrix.md`](feature-matrix.md) measures whether
shipped content is *complete*; this file measures whether a stretch of the game
is *good to play* — and keeps the game-designer pointed at one experience until
it actually is, instead of drifting to the next novel feature.

## Rules

- **One focus at a time.** While the active focus has unmet exit criteria, every
  game-designer cycle must file an issue (or `Depends on:`-chained issue set)
  that advances the focus. The step-6 variety-pressure rotation in
  `game-designer.md` is **suspended** while a focus is active — repetition in
  service of the focus is the point, not a smell.
- **Exit criteria are player-experience statements**, verifiable by playing (or
  by a scripted playthrough), not by data fields existing. A criterion is
  checked off only when the change has **merged**, with the issue/PR linked.
- **Focus-advancing issues are exempt** from the game-designer dedup guard's
  "≥ 3 open game-design issues" limit (same exemption as a decomposed feature's
  chain).
- **Changing focus**: when every exit criterion is checked, the game-designer
  moves the focus to History (with dates) and proposes the next focus in the
  same cycle by editing this file — that edit rides in with the cycle's normal
  flow. A human may override the focus at any time by editing this file; the
  human's choice always wins.
- If this file is missing or has no active focus, the game-designer falls back
  to its normal process (completeness audit, variety pressure).

## Active focus: the new player's first hour

_Declared 2026-07-19 after a manual testplay (human paladin): standard equip
verbs missing, lethal aggressive mobs adjacent to spawn, death within minutes.
The feature matrix was green while the first hour was not fun — this focus
closes that gap._

**Goal**: a brand-new player who types plausible commands and explores
naturally survives their first session, learns the core loop in-game, reaches
level 5, and has a reason to keep playing.

### Exit criteria

- [x] Standard MUD verbs work for arming/wearing gear: `WIELD`/`WEAR`/`HOLD`
      equip, `REMOVE` unequips (#777, merged via #784)
- [x] No unavoidable newbie death: no aggressive mob within 3 rooms of the
      spawn room can kill a level-1 character faster than they can react, and
      any remaining danger is telegraphed *before* the player commits
      (#778 merged via #786, #779 merged via #787, #780 merged via #788)
- [x] The player is armed from creation: newbie kit includes a starter weapon
      (#781, merged via #789)
- [x] Survival commands (`CONSIDER`, `FLEE`, `EQUIP`/`WIELD`) are taught
      in-game before the first fight (#782, merged via #790)
- [x] Every harmful effect a starter-area mob can apply has a counter
      purchasable near the starter area (#783, merged via #792)
- [ ] The starter quest chain walks the player along the safe difficulty ramp
      (training dummy → rat → kobold/goblin → spider), each completion hinting
      at the next step (#795)
- [ ] A scripted golden-path playthrough (extension of `scripts/smoke-test.sh`)
      proves it end-to-end: create character → read hints → win first fight →
      complete first quest → still alive (no issue yet — game-designer to file)
- [ ] The `First session` and `Levels 1–5` rows in
      [`feature-matrix.md`](feature-matrix.md) § Player journeys are all ✅

## History

_(none yet — first focus declared 2026-07-19)_
