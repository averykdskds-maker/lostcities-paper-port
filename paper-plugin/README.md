# LostCitiesPaper (early port)

Native Paper plugin project targeting Minecraft 26.3 / Java 25. It embeds and reads the Modern Tweaks datapack archive from the parent repository.

## Build

The GitHub Actions workflow builds the plugin with JDK 25 and Gradle 9.2.1. The output JAR is named `LostCitiesPaper-0.1.0-alpha1.jar`.

To build locally, run Gradle 9.2.1 with JDK 25 from this folder:

```bash
gradle clean build
```

The build expects `../lostcities-modern-tweaks-v6.0.0.zip` to exist. It embeds that ZIP as `modern-tweaks.zip` inside the plugin JAR.

## Install and create a world

1. Put the built JAR in the Paper server's `plugins` folder.
2. Restart Paper.
3. Run `/lostcities create rotbound_city` (or another unused world name).
4. Teleport to the generated world and explore.

Worlds are never overwritten. This creates a separate world with its own generator. Existing worlds and already-generated chunks are not retrofitted.

Configuration is in `plugins/LostCitiesPaper/config.yml`. Changes apply to worlds created after the next restart/reload of the plugin; do not use PlugMan to reload a live world generator.

## Implemented in this alpha

- Vanilla terrain generation outside city regions.
- Deterministic city-region placement from the world seed.
- Flattened urban plots at a configurable ground level.
- Street chunks and intersections selected from Modern Tweaks' city-style selectors.
- Building definitions, floor segments, and rooftop parts selected from Modern Tweaks JSON.
- Palette JSON parsing, weighted block choices, palette references, and basic variant references.
- City chunks suppress vanilla caves, decorations, and vanilla structures to prevent trees/villages generating through city buildings.

## Not yet a full Lost Cities port

This is a first, testable generation foundation, not a feature-complete reimplementation of McJty's generator. In particular, the original intercity highway planner, railways/monorails, underground city/factory networks, city spheres, terrain profiles, explosions, loot-table/block-entity handling, mob spawners, detailed ruin/rubble rules, and full biome-aware style/condition system are not implemented yet. Some advanced palette metadata and variant behaviours are also not handled. Do not use this alpha as a replacement for the NeoForge mod on a production world.

The original Lost Cities source is MIT licensed; this project keeps the original datapack's included licence in the embedded archive. The Modern Tweaks data remains in its supplied archive and is not modified.
