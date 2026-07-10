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

- Items: `docs/schemas/item.v1.json`
- Rooms: `docs/schemas/room.v1.json`
- Abilities (skills/spells): `docs/schemas/ability.v1.json`
- Attacks: `docs/schemas/attack.v1.json`
- Races: `docs/schemas/race.v1.json`
- Classes: `docs/schemas/class.v1.json`
- Auction listings: `docs/schemas/auction.v1.json` (`data/auctions/listings.json`; the static Auction House definition lives in `data/auctions/auction-house.*.json`, following the `data/banks/bank.*.json` shape)
- Guilds: `docs/schemas/guild.v1.json` (`data/guilds/<guild-id>.json`; one file per guild). Includes an optional, additive `treasury_gold` integer (default `0`) holding the guild's shared bank balance — members deposit with `GUILD DEPOSIT` and the leader withdraws with `GUILD WITHDRAW`. Guild files written before this field existed load with a treasury of `0`.
- Mobs: `docs/schemas/mob.v4.json` (`data/mobs/<mob-id>.json`; one file per mob template). v4 adds an optional `world_boss` boolean flag (default `false`). When `true` the mob is a **world boss**: every online player is notified when it spawns/respawns (`The earth trembles — <name> has awoken in the <room>!`) and when it is slain (`<name> has fallen to <killer> of the <guild>!`, or `... and their party!`), and a kill guarantees at least one rare-or-higher item drop (drawn from the mob's own loot table, reusing the item rarity/affix system) on top of the normal loot rolls. A world boss should therefore list at least one rare-or-higher item in its `loot` table. All `data/mobs/*.json` files carry `schema_version: 4`; the loader requires an exact match, so bumping the constant requires bumping every mob file.

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
