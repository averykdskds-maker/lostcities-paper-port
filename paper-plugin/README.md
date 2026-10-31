# Rotbound

**The Forgotten Earth** — server-side survival for Paper 26.3 / Java 25. Vanilla clients only; no client mods are required.

## What the current alpha generates

- Procedural country-like regions (about 1,024 km square) with generated names and architectural styles. These are not real-world country borders.
- Multiple settlements per 16 km region: metropolitan areas, cities, towns, suburbs, villages, rural settlements, industrial zones, military installations, airports and ports.
- Larger and more frequent settlement footprints than the earliest prototype, with irregular boundaries and connected road patterns derived from the same world seed.
- Initial ruined building shells, airport runway sections, occasional military details and abandoned ship hulls in ocean biomes.
- /rotbound locate city to find the nearest planned metro/city/town; /rotbound locate country for the procedural region name.

The structures are an early procedural placeholder set, not yet detailed buildings such as complete hospitals, police stations, schools, shopping districts, factories or ship interiors.

## Enable it in your normal Overworld

**Back up your server first.** Rotbound only affects chunks generated after it is configured. It does not convert chunks that are already saved.

1. Stop the server.
2. Open the server's bukkit.yml.
3. Find its existing worlds: section. Add or merge this entry under it:

    worlds:
      world:
        generator: Rotbound

Do not overwrite other settings in bukkit.yml. If your Overworld folder is named something other than world, use that exact name.
4. Start the server.
5. Check plugins/Rotbound/config.yml: systems.world-generation should be true, and world.target-world should match your Overworld name.
6. Travel beyond the edge of previously generated chunks, or use a fresh world for a clean test.

The previous foundation alpha stored world generation as disabled. The current build migrates an older Rotbound config once and enables it; after migration, your own config setting is respected.

## Find settlements

- /rotbound locate city — nearest planned metropolitan area, city or town.
- /rotbound locate country — current procedural country-like region.

These commands are operator-only by default. A locate result gives the deterministic plan coordinates; chunks are generated when explored. If the nearest settlement is tens of thousands of blocks away, the larger region scale is intentional.

## Weapons

Use these commands for testing:

    /rotbound weapons
    /rotbound weapon give PlayerName pistol
    /rotbound ammo give PlayerName gunpowder 32

Weapon IDs: pistol, rifle, shotgun, flamethrower, grenade, smg, railgun, tear_gas, mortar, sniper, minigun.

- Pistol, rifle, shotgun, SMG, railgun, sniper and minigun use visible arrow projectiles.
- Grenades use a real Ender Pearl projectile, with vanilla gravity and block collision, but no player teleport.
- Tear gas uses a potion-looking throwable with gravity.
- Flamethrower uses a small fireball.
- Mortar uses a primed TNT entity, follows an arc and is set to detonate when it lands.
- Creative-mode players have infinite ammunition; Survival and Adventure players consume ammo.
- Grenade and mortar blasts do not break blocks.

The minigun uses toggle fire because vanilla server events do not provide reliable continuous right-mouse hold state. It fires two projectiles every four ticks (about 10 rounds/sec), using one gunpowder per volley and the configured heat/cooling system.

## Current limitations

This is a generator foundation, not a map-accurate Earth simulation. Country-like regions are generated procedurally; they do not reproduce actual national borders or city coordinates. The alpha does not yet include a full multi-chunk city master planner, continuous long-distance highways, rail networks, complete airports/ports/ships, dams, bridges, detailed public buildings, sophisticated biome/elevation reclamation, wildlife replacement, environmental mystery placements or structure-specific loot tables.

The normal Overworld generator is enabled only when the Paper/Bukkit world generator is configured. Existing chunks are preserved. Test on a disposable world before deploying to your live multiplayer map.
