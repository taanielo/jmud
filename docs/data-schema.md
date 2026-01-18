# Game Data Schemas

This document describes the on-disk JSON data layout used by the game.

## Directory layout

```
data/
  rooms/
  items/
  characters/
  skills/
```

Each JSON file includes a `schema_version` field. Versioned JSON schemas live under `docs/schemas/`.

## Schemas

- Items: `docs/schemas/item.v1.json`
- Rooms: `docs/schemas/room.v1.json`
- Abilities (skills/spells): `docs/schemas/ability.v1.json`

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
