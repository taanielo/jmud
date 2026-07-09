# TODO

Previous completed backlog archived at `docs/archive/TODO-2026-07-07.md`.

## Planned — Gameplay & Content

- [x] Add item durability: gear degrades in combat and a blacksmith NPC offers a REPAIR command
- [x] Add item rarity tiers (common/uncommon/rare) with stat affixes and colored names in LOOK/INVENTORY
- [x] Add unidentified magic items and an IDENTIFY scroll/NPC service to reveal their stats
- [x] Add locked treasure chests with trap chance and a rogue PICK skill to open them
- [x] Add SNEAK and HIDE stealth commands so rogues can avoid aggressive mobs and set up backstab
- [x] Add STEAL skill for rogues to pickpocket gold from NPCs, with failure aggro consequence
- [x] Add ranged weapons (bows, throwing knives) usable on mobs in adjacent rooms before they close in
- [x] Add buff spells (bless, stoneskin, haste) as timed status effects castable on self and party members
- [x] Add area-of-effect spells that hit every hostile mob in the room, with mana cost scaling
- [x] Add summonable companions: a necromancer-style SUMMON ability that spawns a temporary pet mob fighting for the player
- [x] Add a pet/charm system: tamed mobs follow their owner between rooms and assist in combat
- [x] Add Elf race (high mana, low carry) and Orc race (high strength, healing penalty) with matching starting stats
- [x] Add Ranger class with TRACK skill that points toward the nearest mob of a named type
- [x] Add Paladin class with a self-heal, an undead-damage bonus, and heavy armour proficiency
- [x] Add NPC dialogue trees: TALK command with numbered responses driven by JSON dialogue data
- [x] Add mob factions and reputation: killing one faction's mobs shifts standing and changes NPC prices/aggression
- [x] Add delivery quests: NPC hands the player a package to carry to another NPC in a different zone
- [x] Add exploration quests and achievements for visiting a defined set of rooms (e.g. all Catacombs rooms)
- [x] Add daily quests: one rotating repeatable quest per day (tick-cycle based) with bonus gold/XP
- [ ] Add achievements system: track milestones (first kill, 100 kills, level 10) shown via ACHIEVEMENTS command
- [ ] Add consensual PvP duels: DUEL/ACCEPT commands, fight to near-death in-room, no item/gold loss
- [ ] Add an arena zone with a spectator stand and periodic announced duel events
- [ ] Add player bulletin boards: BOARD/NOTE commands with persistent per-room note storage
- [ ] Add weather system on the tick cycle (rain, fog, storm) shown in outdoor room descriptions with small combat/visibility modifiers
- [ ] Add ambient room messages: zones emit occasional atmospheric lines (dripping water, distant howls) on tick
- [ ] Add MAP command rendering an ASCII minimap of explored rooms around the player
- [ ] Add RECALL scroll/command teleporting the player back to the starter zone, with combat lockout
- [ ] Add boats/ferries: a scheduled transport room that moves between docks on a tick timetable
- [ ] Add The Frozen Peaks high-level zone with ice mobs, a dragon boss, and cold-damage attacks
- [ ] Add customizable prompt: PROMPT command with tokens for HP/mana/moves/XP and color choices
- [ ] Add IGNORE command to mute tells/says from a specific player, persisted per player

## Planned — Technical & Architecture

- [ ] Add graceful shutdown: SIGTERM handler broadcasts a warning, saves all players, and stops the tick loop cleanly
- [ ] Add reconnect support: a dropped connection keeps the player linkdead in-world for N ticks and a new login reattaches to the live session
- [ ] Add tick health metrics: measure per-tick duration and per-Tickable cost, log overruns, expose a wizard-only STATS command
- [ ] Add admin/wizard command set (GOTO, SPAWN, PURGE, SHUTDOWN) gated by a role flag on the user record
- [ ] Add hot-reload of JSON game data: wizard RELOAD command re-reads rooms/items/mobs safely on the tick thread
- [ ] Add a bot-based load test script that connects N telnet clients running scripted commands and asserts tick stability
