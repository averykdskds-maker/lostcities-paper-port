# Rotbound

**The Forgotten Earth** — a server-side Paper 26.3 / Java 25 world generator and survival plugin. Players use ordinary vanilla clients; no client mods or resource pack are required.

## World planning in this build

The world plan is deterministic from the Overworld seed. It creates a synthetic Earth-like settlement hierarchy rather than copying real-world geography:

- Every 8,192 × 8,192-block planning region receives 14 settlement anchors: a metropolis, two cities, three towns, two suburbs, a village, industrial district, military base, airport, port and rural settlement.
- Major urban footprints overlap at their edges to form continuous metropolitan belts instead of isolated dots.
- City street grids use local streets, wider boulevards and ring roads. Highway links are planned between nearby metropolitan areas and cities.
- Buildings are planned on 64-block plots in world coordinates. Individual structures can span chunk borders; their placement, height and damage remain deterministic when chunks are generated in a different order.
- Metropolises and city centres can contain ruined high-rises. Towns, suburbs and villages step down to smaller buildings. Shells have empty window openings, damaged roofs, broken floors, debris and occasional interior saplings.
- Specialist areas include runway/apron/terminal/control-tower airport remains, fenced military compounds and mast structures, industrial warehouses and silos, port piers and cranes, plus occasional abandoned ship hulls.
- Roads and buildings use vanilla blocks and remain server-side.

Country-like regions are currently generated as large named architectural regions. They are **not** actual national borders, coordinates or a map of real Earth. The geography, settlement names and road network are procedural.

## Install and activate

**Back up your server before changing world generation.** Rotbound only affects chunks generated after it is configured. It does not rebuild terrain or structures in already-saved chunks.

1. Stop the Paper server.
2. Put Rotbound-0.1.0-alpha1.jar in plugins/.
3. Open the server's bukkit.yml.
4. Merge this into its existing worlds: section:

    worlds:
      world:
        generator: Rotbound

   Do not delete other bukkit.yml settings. Replace world with the exact Overworld folder name if it differs.
5. Start the server. Rotbound updates an existing v2 config to v3 automatically. Check plugins/Rotbound/config.yml: systems.world-generation should be true, world.target-world must match the world folder and world.structure-density should be 1.0.
6. Use a fresh world for a clean test, or travel beyond all previously generated chunks. Existing chunks remain as they were.

The plugin's startup log tells you the configured target world. It also reminds you that the Bukkit world generator entry is required. /rotbound locate city by itself only reports the seed-based plan; it does not prove that the chunk generator is attached to the world.

## Commands

- /rotbound locate city — coordinates of the nearest planned metro, city or town.
- /rotbound tp city — teleport to the nearest planned centre to test newly generated chunks.
- /rotbound locate country — name and architectural style of the procedural country-like region.
- /rotbound info — project and generation summary.
- /rotbound weapons — list weapons.
- /rotbound weapon give <player> <weapon> — give a test weapon.
- /rotbound ammo give <player> <material> [amount] — give test ammunition.

## Weapons

Weapon IDs: pistol, rifle, shotgun, flamethrower, grenade, smg, railgun, tear_gas, mortar, sniper, minigun.

- Pistol, rifle, shotgun, SMG, railgun, sniper and minigun use visible arrow projectiles.
- Grenades use an Ender Pearl projectile with gravity and block collision, but no player teleport.
- Tear gas uses a potion-looking throwable with gravity.
- Flamethrower uses a small fireball.
- Mortar uses a primed TNT entity and is set to detonate when it lands.
- Creative-mode players have infinite ammunition; Survival and Adventure players consume ammunition.
- Grenade and mortar blasts do not break blocks.

The minigun fires two projectiles every four ticks (about 10 rounds/sec), using one gunpowder per volley and the configured heat/cooling system.

## Important limitations

This is a substantial procedural generation pass, not a finished simulation of the entire real Earth. It does not yet include actual country borders, accurate real-world city placement, a realistic coastline/continent model, complete interiors for every public building, functioning rail networks, bridges and dams, populated wildlife, structure-specific loot, environmental mystery events or the complete biome/elevation reclamation system. Those are separate systems; the similarly named config options are not active just because they exist.

The new generator needs in-game testing on a disposable world. It compiles through the project's GitHub Actions build, but chunk-generation performance, visual quality, terrain transitions and special-structure placement still need live testing on your server.
