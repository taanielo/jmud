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

## No active focus

The "first death & recovery" focus closed on 2026-07-19 (see History). There is
no active focus, so the game-designer falls back to its normal process
(completeness audit, variety pressure) and may propose the next focus in a
future cycle.

## History

### First death & recovery (2026-07-19 → 2026-07-19)

**Goal**: a player who dies — for any reason, at any level — immediately
understands what just happened, where they will wake up (correctly, matching
where they actually respawn), what they lost and what they kept, and how to
get back to their gear, entirely from in-game text, without needing to guess
a command or consult a wiki.

All exit criteria merged:

- [x] The death message names the player's real respawn room (respecting a
      `BIND`-anchored `boundRoomId`) instead of a hardcoded raw room id, and
      teaches the `CORPSE` command as the way back to a dropped corpse; the
      grace-protected message states the grace level so a player understands
      it is temporary (#802, merged via #804)
- [x] `HELP death` (aliases `HELP dying`, `HELP respawn`) explains what
      happens at 0 HP, the grace mechanic, corpse decay, and `RESURRECT`,
      mirroring the existing `HELP hazards`/`HELP combat` static topics
      (#802, merged via #804)
- [x] A scripted playthrough (extension of `scripts/smoke-test.sh`) proves
      the whole journey end-to-end: a character dies (combat or environmental),
      the death message correctly names the real respawn room and mentions
      recovery tools, the player respawns in that same room, and — for a
      non-grace death — `CORPSE` walks them back to their remains (#805 —
      phase 5 forces a real non-grace death on a dedicated `grace_level=0`
      server and asserts the death message, the respawn location, and the
      `CORPSE` route back to the remains)
- [x] The `First death & recovery` row in
      [`feature-matrix.md`](feature-matrix.md) § Player journeys is ✅ across
      every column (#805)

### The new player's first hour (2026-07-19 → 2026-07-19)

**Goal**: a brand-new player who types plausible commands and explores
naturally survives their first session, learns the core loop in-game, reaches
level 5, and has a reason to keep playing.

All exit criteria merged:

- Standard MUD verbs work for arming/wearing gear: `WIELD`/`WEAR`/`HOLD`
  equip, `REMOVE` unequips (#777, merged via #784)
- No unavoidable newbie death: no aggressive mob within 3 rooms of the spawn
  room can kill a level-1 character faster than they can react, and any
  remaining danger is telegraphed *before* the player commits (#778 merged
  via #786, #779 merged via #787, #780 merged via #788)
- The player is armed from creation: newbie kit includes a starter weapon
  (#781, merged via #789)
- Survival commands (`CONSIDER`, `FLEE`, `EQUIP`/`WIELD`) are taught in-game
  before the first fight (#782, merged via #790)
- Every harmful effect a starter-area mob can apply has a counter purchasable
  near the starter area (#783, merged via #792)
- The starter quest chain walks the player along the safe difficulty ramp
  (training dummy → rat → kobold/goblin → spider), each completion hinting at
  the next step (#795)
- A scripted golden-path playthrough (extension of `scripts/smoke-test.sh`)
  proves it end-to-end: create character → read hints → win first fight →
  complete first quest → still alive (#798 — phase 3e drives create → hints →
  WIELD → accept rat-catcher → kill Giant Rats to 5/5 → QUEST COMPLETE →
  SCORE shows positive HP + 30g/75xp reward + Rat Slayer title; also fixed the
  rats wandering unbounded out of the hollow, `data/mobs/rat.json`
  `wanders: false`)
- The `First session` and `Levels 1–5` rows in
  [`feature-matrix.md`](feature-matrix.md) § Player journeys are all ✅ (both
  rows now ✅ across every column after #798 flipped Scripted playthrough)
