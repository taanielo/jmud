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

## Examples

Item example (`data/items/iron-sword.json`):

```json
{
  "schema_version": 1,
  "id": "iron-sword",
  "name": "Iron Sword",
  "description": "A plain iron sword with a worn leather grip."
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
