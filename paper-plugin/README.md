# Rotbound

**The Forgotten Earth** — a server-side survival project for Paper 26.3 / Java 25.

Rotbound is designed for ordinary vanilla Minecraft clients. No Fabric, Forge, NeoForge or client-side mods are required. Optional server resource packs may be considered later, but the project must remain playable without one.

## Project direction

The world is Earth roughly 50–100 years after humanity disappeared. It is not a conventional wasteland: nature has reclaimed settlements at different rates depending on biome, elevation, climate and proximity to water. The intended atmosphere is quiet, abandoned and unexplained.

Core design requirements:

- The normal Overworld is the intended playable world; a separate city dimension is not the end goal.
- Natural terrain, rivers, lakes and coastlines remain the foundation.
- Human infrastructure is fragmented, damaged, flooded, buried or reclaimed by vegetation.
- City planning must be continuous and planned across chunks, not a repeating circular grid of independent building chunks.
- Reclamation and structure condition must account for biome and elevation.
- Mystery props are rare and unexplained.
- Wildlife is wild and appropriate to its environment; no active farms or human civilization.
- Loot is location-specific, scarce and non-refilling. Exceptional equipment must be rare.
- Weapons are server-authoritative and identified by Persistent Data Container tags, not display names alone.
- Decorative heads and display entities may be used for visuals; physical collision must be provided separately where needed.

## Current foundation

Implemented in this build:

- Rotbound plugin identity and main entry point.
- /rotbound command framework.
- Server-side tagged weapon items and ammunition consumption.
- Initial mechanics for pistol, rifle, shotgun, flamethrower, grenade, SMG, railgun, tear gas, mortar, sniper and minigun.
- Configurable weapon cooldowns and selected weapon parameters.
- Minigun toggle-fire mode, ammunition use and heat/cooling.

Not yet implemented:

- The Forgotten Earth terrain and city planner.
- Normal Overworld generator integration.
- Biome/elevation-aware reclamation.
- Ruined structure library and multi-chunk building placement.
- Structure-specific loot population and anti-refill protection.
- Wildlife distribution and environmental storytelling.
- Custom decorative-head catalogue and display-entity system.
- Weapon balancing on a live server, advanced projectile/penetration behaviour, weapon durability and complete PvP/PvE rules.

**Important:** World generation, reclamation, structures, loot and wildlife are disabled in this foundation build. This prevents the old experimental circular city-grid generator from being mistaken for the intended Rotbound world.

## Build

The GitHub Actions workflow builds with Temurin Java 25 and Gradle 9.2.1.

For a local build, run from this directory:

    gradle --no-daemon clean build

Output: build/libs/Rotbound-0.1.0-alpha1.jar

## Install and test weapons

1. Put the built JAR in the Paper server's plugins directory.
2. Start Paper 26.3 with Java 25.
3. Run /rotbound weapons to list weapon IDs.
4. As an operator, give a test weapon with /rotbound weapon give <player> <weapon-id>.
5. Give ammunition with /rotbound ammo give <player> <material> [amount].

Example:

    /rotbound weapon give PlayerName pistol
    /rotbound ammo give PlayerName gunpowder 32

Weapon IDs: pistol, rifle, shotgun, flamethrower, grenade, smg, railgun, tear_gas, mortar, sniper, minigun.

The minigun currently uses toggle firing: right-click once to start and again to stop. It fires two snowballs per four ticks (about 10 projectiles per second), consumes one gunpowder per volley and accumulates heat.

This is an early development foundation. Test on a disposable world before allowing players to use it on a live server. Explosive, railgun, projectile and damage behaviour still needs multiplayer testing and balancing.
