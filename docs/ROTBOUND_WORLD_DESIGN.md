# Rotbound — The Forgotten Earth
Version: design baseline 1.0

## Premise and tone

The setting is Earth approximately 50–100 years after humanity has left or died out. There is no active civilization. The world is not a freshly destroyed wasteland: it has been forgotten, and nature is reclaiming it at different speeds.

The dominant mood is quiet. Wind, water and birds should be more common than music or combat sounds. The world should not explain what happened. There are no bodies, blood trails or explicit violence clues.

## Non-negotiable technical constraints

- Paper server plugin; all authoritative systems run on the server.
- Vanilla client compatibility. No Fabric, Forge, NeoForge or mandatory client mods.
- No client-side rendering dependency.
- Custom player heads, vanilla block states and display entities are permitted for server-sent visuals.
- Display entities are visual-only; collision and interaction must use real blocks or explicit server-side interaction logic.
- A server resource pack may be optional, but the world must remain playable without one.
- The intended world is the normal Overworld, not a separate city dimension.
- Existing generated chunks must never be silently or destructively rewritten. New generation must be deterministic and chunk-border-safe.
- The current experimental circular city-grid generator is retired. Its output is not the target design.

## Natural world baseline

Start with natural terrain as the foundation: topography, caves, rivers, lakes, oceans and biomes. Mountains should remain the closest thing to pristine reference landscapes and should have sparse human remains.

Water systems should appear natural rather than actively managed. Rivers and lakes should follow natural channels. Former dams and reservoirs may be breached or dry; dried reservoirs can contain cracked mud, exposed sediment and dead trees. Low-lying coastal settlements should be flood-prone, with tides and salt damage reaching former streets.

## Reclamation by environment

| Environment | Reclamation appearance |
|---|---|
| Plains | Former farmland becomes meadow, scrub and young woodland. Field boundaries disappear. Rare rusted machinery is partly buried. |
| Taiga | Forest gradually reclaims settlements and roads. |
| Desert | Sand buries most structures; only taller buildings or upper floors remain exposed. Oases are natural. |
| Jungle | Dense canopy, vines and strangler growth obscure structures almost completely. |
| Savanna | Tall grass, scattered trees and dry-season erosion reclaim infrastructure. |
| Snowy / tundra | Permafrost cracks foundations, vegetation is stunted, roofs collapse beneath snow and structures become whiteout silhouettes. |
| Ocean / coast | Low-lying urban areas flood. Salt crusts, tidal deposits and submerged streets are common. |
| Mountains | Mostly natural, with rare remnants of roads, observatories, communication infrastructure or isolated buildings. |

Reclamation must be driven by biome, elevation, climate, hydrology and local terrain—not by one global percentage of vines or moss.

## Historical human footprint

Civilization should not be distributed as uniform circles. Human footprints should consist of connected and disconnected settlement types:

- Large cities with a dense core, suburbs, industrial areas and transport corridors.
- Smaller towns, villages and isolated rural properties.
- Highways, rail corridors, bridges, tunnels and service stops.
- Ports, airports, dams, power stations and communication facilities.
- Farms whose former boundaries disappear into meadow or forest.

The generator should plan districts and road networks over a larger region before placing block geometry. Roads must connect across chunk boundaries. Blocks and buildings may span multiple chunks. Each structure must have a deterministic plan shared by every chunk it touches.

## Structures and decay

### Skyscrapers
Hollow interiors, missing glass, exposed floors, partial floor collapses, water ingress, dirt and saplings inside. Some remain standing; others are partially collapsed.

### Suburbs
Mostly single-storey homes, with flattened or roofless sections and overgrown yards. Streets and property boundaries are broken and obscured.

### Highways
Cracked, sunken asphalt with grass and trees growing through joints and gaps. Rare rusted vehicles have no wheels or glass.

### Power infrastructure
Lines are snapped, drooping or absent. Towers may remain, but there is no electricity. No powered lighting or active machinery.

### Water infrastructure
Pipes burst. Buildings may contain standing water. Fountains are dry or stagnant. Dams and reservoirs are breached, drained or partially reclaimed.

### Other
Police stations, hospitals, schools, factories, shops, offices, laboratories, military facilities, bunkers, farms, railway stations, radio towers, garages and utility buildings must have distinct construction and decay profiles.

No intact fences, clean roads, active lights, crops or maintained landscaping.

## Environmental storytelling

Mystery details should be individually rare, not repeated decorations:

- One open door in an otherwise sealed building.
- A dinner table still set and untouched.
- A child's toy in the middle of a road.
- A radio tower still standing, without power or signal.

There is no explanation text, quest marker or forced narrative. Discovery is the narrative.

## Wildlife

Only wild fauna should populate the environment. Candidate species include wolves, foxes, rabbits, birds, fish, boars, occasional bears and deer. Distribution should depend on biome and habitat. No farm animals requiring human care, no active livestock or crops.

Wildlife populations must remain sparse enough to preserve the quiet atmosphere. Hostile mobs, if retained as a gameplay system, must not turn every city into a conventional monster arena.

## Loot and multiplayer balance

Buildings should not be guaranteed loot caches. Loot is generated once, is tied to building type and condition, and does not refill when containers are reopened or chunks reload.

Loot tiers:
- Everyday supplies: common.
- Useful salvage: uncommon.
- Valuable equipment and materials: rare.
- Exceptional weapons, high-end armour and powerful enchantments: extremely rare.

A house should mostly contain ordinary household remnants. Police stations, military sites, laboratories and industrial facilities have appropriate themed loot pools, but most containers can be empty or stripped.

The player should progress through survival, exploration, crafting, trade and cooperation—not by looting one building.

## Weapons

The 11 server-side weapons are specified in ROTBOUND_WEAPONS_AND_LOOT.md. Weapons are tagged with Persistent Data Container identifiers. Display names alone must never grant weapon functionality. Ammunition is consumed from player inventory, and PvP/PvE balance must be separately testable.

Explosives should not destroy generated architecture or terrain by default. Any block damage must be explicitly configured and protected against abuse.

## Generation architecture target

The intended pipeline is:

1. **Natural terrain:** establish biome, elevation, terrain shape, water and climate data.
2. **Historical footprint:** deterministically plan settlements, districts, roads, industrial land and isolated infrastructure over region-scale coordinates.
3. **Structure planning:** place complete multi-chunk structures and road networks before block placement. Every chunk must query the same region plan.
4. **Reclamation:** apply biome/elevation-aware erosion, flooding, vegetation, snow, sand burial, water ingress and collapse states.
5. **Storytelling and loot:** place rare set-dressing and non-refilling structure-specific loot from the same deterministic structure identity.
6. **Wildlife:** place sparse biome-appropriate wildlife and habitat features.

All stages must use seed-derived deterministic randomness and stable world coordinates. No chunk may independently invent a different road direction, building footprint or structure variant from its neighbours.

## Explicitly rejected aesthetic

- Uniform circular cities filled with a repeating fishnet road grid.
- A clean grid of identical city plots.
- Vines pasted over otherwise intact modern buildings.
- Universal rubble piles and constant explosions.
- Nuclear wasteland colours, mass graves, blood or gore.
- Explanation books, lore dumps or scripted horror events.
- Every building being full of valuable loot.
- A mandatory client modpack.
