This directory stores JSON data for the game world.

Layout:
- rooms/ (room definitions)
- items/ (item definitions)
- characters/ (character definitions)
- skills/ (skills and spells)
- attacks/ (attack definitions)
- races/ (race definitions)
- classes/ (class definitions)
- users/ (authentication credentials, one JSON file per user — separate from players/ character state)
- guilds/ (player-founded persistent guilds, one JSON file per guild — see docs/schemas/guild.v1.json; created and mutated at runtime by GuildService)

See docs/data-schema.md for schema details and examples.
