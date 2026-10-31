# LostCitiesPaper — Paper 26.3

A server-side Paper port of McJty's Lost Cities 26.3 generation engine, bundled with the Lost Cities Modern Tweaks 6.0.0 datapack assets.

**This is an alpha source port.** It compiles against Paper 26.3 / Java 25 and packages the original terrain-generation engine and asset files. It has passed an automated live-server smoke test on Paper 26.3 build 235: the plugin enabled at Paper's STARTUP phase, its datapack and profile files loaded, and the original FEATURES-stage generator ran for chunk `(0,0)` before Paper finished preparing the spawn area. A forced distant chunk was also loaded without a generation error. Broader biome, structure, multiplayer and long-running tests are still outstanding, so keep backups and test on a disposable world first.

## What is included

- The upstream Lost Cities chunk-generation implementation, including its city, building, street, highway, railway, bridge, damage, rubble and terrain-generation logic.
- The upstream profile definitions and JSON profile loader.
- The original Lost Cities 26.3 JSON assets.
- The Lost Cities Modern Tweaks 6.0.0 JSON assets, merged into the same embedded datapack.
- A Paper FEATURES-stage integration that passes Paper's actual world-generation region to the original Lost Cities generation code.
- A single plugin JAR. Players do not need Lost Cities, Modern Tweaks, NeoForge, a client mod or a separate datapack.

The port adapts the mod-loader bootstrap, configuration access, asset registry loading, event hooks and Paper integration. It does not intentionally replace the original city-generation rules with a new generator.

## Requirements

- Paper 26.3
- Java 25
- A new or disposable test world for the first run

## Install

1. Stop the Paper server.
2. Put `LostCitiesPaper-0.1.0-alpha1.jar` in the server's `plugins/` directory.
3. Start the server once. The plugin creates `plugins/LostCitiesPaper/config.yml`, `config/lostcities-server.toml` and the profile JSONs under `config/lostcities/profiles/`.
4. The bootstrapper registers the embedded datapack during Paper's datapack-discovery phase; it is automatically enabled on startup. Confirm it with `/datapack list enabled`.

No `bukkit.yml` generator entry is required. The plugin attaches its populator to the world selected by `target-world` in `plugins/LostCitiesPaper/config.yml`.

## Configuration

### Paper-facing settings

File: `plugins/LostCitiesPaper/config.yml`

```yaml
enabled: true
target-world: world
```

- `enabled`: disables Lost Cities generation when false.
- `target-world`: exact Bukkit world folder/name to which the Lost Cities populator is attached. Default: `world`.

### Server settings

File: `config/lostcities-server.toml`

This follows the upstream configuration layout. The `[profiles]` section selects the active profile and maps dimension identifiers to profiles. The `[general]` section contains server-wide options such as heightmap optimization, cache cleanup and structure avoidance.

### Generation profiles

Folder: `config/lostcities/profiles/`

The plugin writes the upstream standard profiles here on startup. Each profile JSON contains the detailed Lost Cities generation settings (city chance, city radius, building density, floor ranges, streets, parks, highways, railways, explosions, rubble, foliage, spawn rules, and more).

**To make your own profile:** copy a standard profile JSON to a new filename, for example `rotbound.json`, then edit the values in that new file. Set `selectedProfile = "rotbound"` under `[profiles]` in `config/lostcities-server.toml`, or use:

```
/lostcities profile rotbound
```

Avoid editing the built-in profile JSON files directly: the upstream profile bootstrap writes those defaults again during startup. A separately named custom profile is loaded after the standard profiles and is retained.

After editing a custom profile JSON or `lostcities-server.toml`, run:

```
/lostcities reload
```

or restart the server.

### Commands

- `/lostcities status` — show the selected profile, target world and config paths.
- `/lostcities profile <name>` — select and persist a profile.
- `/lostcities reload` — reload the server settings and JSON profiles.

Profile changing and reload require the `lostcities.admin` permission (default: operators).

## World-generation notes

- The plugin loads at Paper's `STARTUP` phase and attaches its populator during `WorldInitEvent`, before initial spawn-area generation. The smoke test confirmed generation began in chunk `(0,0)` before Paper reported the spawn area prepared.
- Generation applies only to chunks generated after the populator has been attached.
- Existing chunks are not regenerated or converted. Use a fresh world for a clean test.
- Changing profiles affects newly generated chunks; already generated chunks keep their existing blocks.
- The plugin targets one Bukkit world name at a time through `target-world`.
- The CI smoke test forced a new chunk at chunk coordinates `(100,100)` using the default profile on Paper 26.3 build 235. It confirmed the original generator callback completed without a logged chunk-generation error; it is not a substitute for broad playtesting across all biomes, profiles and world conditions.
- Back up the world before testing.

## Source and licensing

- Lost Cities source: [McJtyMods/LostCities](https://github.com/McJtyMods/LostCities), branch `26.3_neo`, pinned in the build workflow. The upstream source includes its MIT license.
- Modern Tweaks assets: the v6.0.0 datapack ZIP is bundled with its included license file.
- Paper integration and adapters are maintained in this repository under `lostcities-paper/`.
