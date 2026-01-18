# Effects

Effect definitions live in this folder as JSON files.
Each file represents one effect definition and is loaded by id.

## File layout

```
effects/
  stoneskin.json
```

## JSON schema (v1)

Required fields:
- `schema_version` (int, currently `1`)
- `id` (string, `^[a-z0-9-]+$`)
- `name` (string)

Optional fields:
- `duration_ticks` (int, default `0`)
  - `0` means permanent until explicitly removed.
- `tick_interval` (int, default `1`)
  - Reserved for future periodic effects.
- `stacking` (string, default `refresh`)
  - `refresh`: reset duration
  - `stack`: increment stacks and extend duration
  - `ignore`: do nothing if already active
  - `replace`: reset stacks to 1 and reset duration
- `modifiers` (array)
  - `stat`: stat name (string)
  - `op`: `add` | `multiply`
  - `amount`: integer
- `messages` (object)
  - `apply_self`, `apply_room`, `expire_self`, `expire_room`, `examine`

Message placeholders (if used in the future) should follow `{name}` for the target name.

## Player persistence

Player save data stores only minimal effect state:
- `effect id`
- `remaining ticks`
- `stacks`

On login, effect definitions are rehydrated from this folder.
