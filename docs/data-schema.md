# Game Data Schemas

This document describes the on-disk JSON data layout used by the game.

## Directory layout

```
data/
  rooms/
  items/
  characters/
  skills/
  attacks/
  races/
  classes/
```

Each JSON file includes a `schema_version` field. Versioned JSON schemas live under `docs/schemas/`.

## Schemas

- Items: `docs/schemas/item.v1.json` (latest: `docs/schemas/item.v15.json`; `data/items/*.json`, one file per item). The loader accepts any of `schema_version` 3–15, so older item files load unchanged. Each newer version adds one optional, additive, backward-compatible change: v6 `light_radius`, v7 `max_durability`/`durability`, v8 `rarity`/`affixes` (rarity tiers, lowest to highest: `common`, `uncommon`, `rare`, `epic`; an absent value means `common`. `epic` is the apex tier reserved for hand-authored, boss-exclusive signature loot — it ranks strictly above `rare`, renders in bold magenta, and has its own SALVAGE yield tier), v9 `identified`, v10 `locked`, v11 `two_handed`, v12 `mount_move_discount`, v13 adds the `neck` and `finger` values to the `equip_slot` enum (amulet/necklace and ring slots, one item each, worn and validated exactly like the existing seven slots with no combat special-casing), v14 adds the `epic` rarity tier and `map_area_id` (map items), v15 adds the `back` value to the `equip_slot` enum (a worn pack slot; back-slot items carry a `"carry"` stat in `attributes.stats` that adds a flat bonus to the wearer's max carry weight, summed exactly like armour `"ac"` — worn and validated like the existing slots with no combat special-casing). v11's `two_handed` is an optional boolean (default `false`) marking a weapon as needing both hands: equipping a two-handed weapon into the `weapon` slot auto-unequips any `offhand` item back into the pack, and while a two-handed weapon is worn, equipping an `offhand` item (shield or second weapon) is refused — so the dual-wield off-hand attack and shield block never apply. Two-handed weapons are flagged `(two-handed)` in EQUIPMENT and EXAMINE. v12's `mount_move_discount` is an optional positive integer marking a carried item as a rideable mount (a pony, a warhorse): a player `MOUNT`s the item to reduce each outdoor step's move-point cost by that amount (floored at zero), and `DISMOUNT`s (or is thrown off automatically on entering combat or moving indoors) to return to normal. The ridden state is transient and never persisted, though the mount item stays in inventory. One-handed weapons, non-mount items, and item files lacking these fields behave exactly as before.
- Rooms: `docs/schemas/room.v1.json`
- Abilities (skills/spells): `docs/schemas/ability.v1.json`
- Attacks: `docs/schemas/attack.v1.json` (latest: `docs/schemas/attack.v6.json`; `data/attacks/*.json`, one file per attack). The loader accepts `schema_version` 2–6, so older attack files load unchanged. Each newer version adds one optional, additive, backward-compatible field: v3 `weapon_type` (default `SLASHING`), v4 `applies_effect` (on-hit status effect), v5 `range_type` (default `MELEE`), v6 `damage_type` (default `PHYSICAL`). v6's `damage_type` is one of `PHYSICAL`, `FIRE`, `COLD`, `POISON`: a non-physical attack has its computed damage reduced by the defender's matching `*_resist` armour stat (see below), capped at 75% so resistance never grants full immunity. Attack files that omit `damage_type` deal physical damage exactly as before.
- Races: `docs/schemas/race.v1.json`
- Classes: `docs/schemas/class.v1.json`
- Auction listings: `docs/schemas/auction.v1.json` (`data/auctions/listings.json`; the static Auction House definition lives in `data/auctions/auction-house.*.json`, following the `data/banks/bank.*.json` shape)
- Bounties: `docs/schemas/bounty.v2.json` (`data/world-state/bounties.json`; the single world-scoped ledger of open player-funded bounties, parallel to `data/world-state/discovered-exits.json`). Each entry is one backer's `reward` gold stake on a `target_kind` of `MOB` or `PLAYER`, identified by `target_id` (a mob template id, or the target player's username) with `target_name` captured at post time for listings/announcements, posted at `posted_tick`; `BOUNTY POST` appends an entry (at most one per `(backer, target_kind, target_id)` triple), `BOUNTY CANCEL` removes and refunds the backer's own entry, a kill of a bountied mob type removes every matching mob entry and pays the pooled reward to the killer, and a duel loss removes every matching player entry and pays the pooled reward to the winner. The set is held in memory and mirrored to disk write-behind, so the tick-thread payout check never reads from disk; the loader is defensive (missing/empty/malformed file → no bounties) and the file is additive — its absence just means no bounties are open. The loader accepts `schema_version` `2` (generic `target_*` fields) and legacy `1` (mob-only `mob_template_id`/`mob_name`, loaded unchanged as `MOB`-kind bounties).
- Guilds: `docs/schemas/guild.v3.json` (`data/guilds/<guild-id>.json`; one file per guild). Includes an optional, additive `treasury_gold` integer (default `0`) holding the guild's shared bank balance — members deposit with `GUILD DEPOSIT` and the leader withdraws with `GUILD WITHDRAW`. v2 added the additive shared item vault (`vaulted_items`, default `[]`). v3 adds the additive `lifetime_deposited_gold` integer (default `0`): the running total of all gold ever deposited (withdrawals never reduce it), which drives the guild's level (1-5, thresholds `0/500/2,000/5,000/15,000`) and its level-scaled shared-vault capacity (`40/50/60/70/80` slots). Guild files written before a field existed load with that field at its default (treasury `0`, empty vault, lifetime `0` → guild level 1); `MIN_SCHEMA_VERSION` stays at `1` so v1/v2 files keep loading.
- Shops: `docs/schemas/shop.v2.json` (`data/shops/shop.<shop-id>.json`; one file per shop). Bound to a single room via `room_id`; `stock` lists purchasable items with an optional per-item `price` override. An optional `faction_id` ties the shop to a faction so buy/sell prices shift with the player's reputation. v2 adds an optional per-stock-entry `min_reputation` gate: an entry with `min_reputation` is only purchasable by a player whose standing with the shop's `faction_id` is at least that value; below it the entry lists as `[locked — requires better standing with <faction>]` and `BUY` is refused. Omitting the field preserves the v1 always-purchasable behaviour, so faction-neutral shops and ungated entries load unchanged. The loader requires an exact `schema_version` match.
- Mobs: `docs/schemas/mob.v4.json` (`data/mobs/<mob-id>.json`; one file per mob template). v4 adds an optional `world_boss` boolean flag (default `false`). When `true` the mob is a **world boss**: every online player is notified when it spawns/respawns (`The earth trembles — <name> has awoken in the <room>!`) and when it is slain (`<name> has fallen to <killer> of the <guild>!`, or `... and their party!`), and a kill guarantees at least one rare-or-higher item drop (drawn from the mob's own loot table, reusing the item rarity/affix system) on top of the normal loot rolls. A world boss should therefore list at least one rare-or-higher item in its `loot` table. All `data/mobs/*.json` files carry `schema_version: 4`; the loader requires an exact match, so bumping the constant requires bumping every mob file. The optional free-form `tags` array (default `[]`) attaches behavioural/classification flags that need no schema bump because they are additive strings the engine matches by name: `"npc"` marks a non-attackable townsfolk/shopkeeper, `"fearless"` stops a wounded mob from ever fleeing, and `"pack"` marks a **pack** mob. A `pack` mob never starts a fresh fight on its own — it only reinforces one already begun (by a player attacking any mob in the room, or by an aggressive/faction room-mate): on its own AI tick, if a different alive mob in its room already has a player engaged, the pack mob joins the fight against that same player as an extra attacker (announced with `Another <name> snarls and lunges to its packmate's defense!`) and thereafter attacks/flees/taunts like any other engaged mob. Stealth still prevents a pack mob's own *first* engagement exactly as it does an aggressive mob's, and the tag is inert on `world_event`/`world_boss` templates (they already fight solo by design). An optional `parry_chance` integer (0-100, default `0`, no schema bump) marks a **defensively trained** melee mob: on that percentage of a player's otherwise-landing *melee* swings (both the initial `KILL`/`ATTACK` strike and the per-tick auto-attack) the mob fully parries — the player deals zero damage — and ripostes with its own `attack_id`, damaging the attacker (`The <mob> parries your attack and <verb>s you for N!`, or the attack's authored `attack_parry` message when provided). The effective chance is clamped to combat's `MAX_PARRY_CHANCE` at resolution time, and only melee player attacks are eligible: ranged (`SHOOT`), AoE-spell, and summoned-pet damage never roll it. Author a non-zero value only on guards, elite humanoid combatants, and boss-tier fighters; omit it (or set `0`) for ordinary mobs, which never parry. An optional `healer` boolean (default `false`, no schema bump) marks a **support-caster healer** mob: on its own AI decision, if a different alive, non-pet mob in its room is wounded at or below `heal_threshold` percent of that ally's max HP (default `50`) and not already full, the healer restores a random `heal_min`..`heal_max` HP to the most-wounded such ally (clamped to the ally's max, ties broken most-wounded-first) and announces it to the room (`The <mob> chants over the <ally>'s wounds — it looks steadier!`) instead of attacking that decision; when no ally needs healing it attacks normally via `attack_id`. `heal_min`/`heal_max` are required (positive, `heal_max >= heal_min`) when `healer` is `true` and ignored otherwise. Healing composes with the other AI branches — a healer mid-telegraph or mid-flee does not also heal that decision — and `CONSIDER`/`CON <mob>` shows a discoverability hint (`It looks like it could heal its allies.`). Author it on support mobs placed in multi-mob encounters (e.g. the Bandit Medic in the Broken Ruins bandit camp); omit it for ordinary mobs, which never heal.
- Recipes: `docs/schemas/recipe.v1.json` (`data/recipes/*.json` for the blacksmith's CRAFT, `data/recipes/alchemy/*.json` for the alchemist's BREW, `data/recipes/cooking/*.json` for the cook's COOK; one file per recipe). A recipe turns the listed `materials` plus `gold_cost` into one copy of `output_item`. Two optional, additive fields gate and reward crafting proficiency: `min_skill` (integer, default `0`) is the minimum profession level required to attempt the recipe — locked recipes list as `[requires <profession> <n>]` and crafting them is refused with no material/gold consumption; `proficiency_gain` (integer, default `25`) is how many proficiency points a successful craft grants in the relevant profession (blacksmithing/alchemy/cooking), where every 100 points is one level. Recipes that omit both fields behave exactly as before (craftable by anyone, default gain). The loader requires an exact `schema_version` match of `1`; both new fields live inside v1, so existing recipe files load unchanged.

## Examples

Item example (`data/items/iron-sword.json`):

```json
{
  "schema_version": 1,
  "id": "iron-sword",
  "name": "Iron Sword",
  "description": "A plain iron sword with a worn leather grip.",
  "attributes": {
    "stats": {
      "strength": 1
    }
  },
  "effects": [],
  "value": 25
}
```

Room example (`data/rooms/training-yard.json`):

```json
{
  "schema_version": 1,
  "id": "training-yard",
  "name": "Training Yard",
  "description": "A dusty yard with practice dummies and scattered weapons.",
  "item_ids": [
    "iron-sword",
    "health-potion"
  ]
}
```

Ability example (`data/skills/spell.heal.json`):

```json
{
  "schema_version": 1,
  "id": "spell.heal",
  "name": "heal",
  "type": "SPELL",
  "level": 1,
  "cost": {
    "mana": 4
  },
  "cooldown": {
    "ticks": 3
  },
  "targeting": "BENEFICIAL",
  "aliases": ["healing"],
  "effects": [
    {
      "kind": "VITALS",
      "stat": "HP",
      "operation": "INCREASE",
      "amount": 6
    }
  ],
  "messages": {
    "self": "You cast {ability} on {target}.",
    "target": "{source} casts {ability} on you.",
    "room": "{source} casts {ability} on {target}."
  }
}
```

Message templates support `{source}`, `{target}`, and `{ability}` placeholders.

Attack example (`data/attacks/attack.unarmed.json`):

```json
{
  "schema_version": 1,
  "id": "attack.unarmed",
  "name": "unarmed",
  "min_damage": 1,
  "max_damage": 3,
  "hit_bonus": 0,
  "crit_bonus": 0,
  "damage_bonus": 0
}
```

### Elemental resistance item stats (no item schema bump)

Armour resists elemental damage through the item's existing free-form `attributes.stats`
map — the same `string -> int` mechanism that already carries `ac`. A stat key named
`<element>_resist` (e.g. `fire_resist`, `cold_resist`, `poison_resist`) with a whole-number
value means "reduce incoming damage of that element by that percent". Because `stats` is
already a generic map, **no item schema version bump is required** to itemize resistance —
existing items simply gain a new conventional key. Resistances from every equipped slot are
summed by `EquipmentResistanceResolver` (mirroring `EquipmentArmorResolver.totalAc`) and the
total is capped at `CombatSettings.maxResistancePercent()` (default 75) at the point of
mitigation. `EXAMINE`, `COMPARE`, and `EQUIPMENT` surface these stats the same way `ac` is
already surfaced. Example: a cloak with `"cold_resist": 25` cuts a frost wyrm's glacial-breath
damage by a quarter.

Race example (`data/races/race.elf.json`):

```json
{
  "schema_version": 1,
  "id": "elf",
  "name": "Elf",
  "healing": {
    "base_modifier": -2
  }
}
```

Class example (`data/classes/class.mage.json`):

```json
{
  "schema_version": 1,
  "id": "mage",
  "name": "Mage",
  "healing": {
    "base_modifier": -1
  }
}
```

## Player save files

Player state lives in `players/<username>.json` at the project root (runtime state, not
committed game content). Unlike the content schemas above, player saves have **no
`schema_version` field**: `Player` is serialised directly by Jackson
(`JsonPlayerRepository`), and every persisted field is optional on load. New fields are
added additively — a missing property maps to a sensible default via the
`@JsonCreator` constructor — so old save files always load without loss.

Duel-record fields (added for the persistent PvP `RANK DUELS`/`SCORE` records):

- `duelWins` (integer, default `0`) — duels won by combat resolution. Incremented only
  when a duel actually resolves (a participant is reduced to 0 HP); forfeits and timeouts
  never change it.
- `duelLosses` (integer, default `0`) — duels lost by combat resolution, with the same
  forfeit/timeout exclusion.

Both counters are clamped to be non-negative. A save file written before these fields
existed loads with `duelWins`/`duelLosses` of `0` and all other fields intact.
