# Rotbound — Weapons, Loot and Multiplayer Balance
Version: design baseline 1.0

## Weapon implementation constraints

All weapons are server-side Paper mechanics using renamed/tagged vanilla items. Identify weapons by a unique Persistent Data Container key; never trust an item's display name. No client-side mods are required.

Projectiles should be silent. Weapon audio should be played separately to the shooter. If custom audio is ever added, it must use an optional server resource pack with a vanilla fallback.

Ammunition is consumed from inventory. Empty weapons stop firing. Weapon items themselves must not accidentally be consumed as ammunition when their base material matches the ammo material.

Disable ordinary melee damage while a tagged weapon is held. Commands are for administration/testing; normal players should primarily find weapons in appropriate abandoned structures.

## Weapon catalogue

| # | Weapon | Identity item | Mechanic | Ammunition |
|---:|---|---|---|---|
| 1 | Pistol | Stick | One snowball projectile; speed multiplier 4; 6 damage | Gunpowder, 1 per shot |
| 2 | Rifle | Bow | Arrow velocity about 6; 10 damage | Arrow, 1 per shot |
| 3 | Shotgun | Crossbow | 5–7 snowballs in a spread; 2 damage each | Arrow, 1 per shot |
| 4 | Flamethrower | Blaze Rod | Short-range small fireball; velocity about 2.5; ignites targets | Blaze Rod, 1 per shot |
| 5 | Grenade | Ender Pearl | Thrown projectile; one-second fuse after impact; small blast | Ender Pearl, 1 per shot |
| 6 | SMG | Iron Sword | Three arrows at five-tick intervals; 3 damage each | Arrows, 3 per burst |
| 7 | Railgun | Diamond Pickaxe | High-speed shot; 15 damage; area effect within 2 blocks; intended to penetrate walls | Diamond, 1 per shot |
| 8 | Tear Gas | Water Bottle | Impact cloud; Blindness and Slowness III for 8 seconds within 6 blocks; no direct damage | Water Bottle, 1 per shot |
| 9 | Mortar | TNT | 45-degree arcing TNT; velocity about 3.5; yield 4 | TNT, 1 per shot |
| 10 | Sniper | Nether Star | Arrow velocity about 15; 25 damage; 10-second cooldown | Nether Star, 1 per shot |
| 11 | Minigun | Iron Pickaxe | Two snowballs every four ticks (about 10 projectiles/sec); heat rises by 5 per volley; overheats at 100; cools by 3 per 10 ticks | Gunpowder, 1 per volley |

The user-specified minigun rate was “two snowballs every two ticks” and approximately 10 rounds/sec, which conflict mathematically. The current foundation uses two snowballs every four ticks to match approximately 10 projectiles/sec. This is configurable.

## Multiplayer safeguards

- Server-side authoritative hit detection and ammunition consumption.
- Per-weapon cooldowns, not one shared player cooldown.
- Configurable projectile damage, effects, range, spread and cooldown.
- Separate PvP and PvE balancing before release.
- Prevent creative-mode or command-granted test items from becoming loot duplication paths.
- Explosions should not damage blocks by default. Mortar and grenade block damage must remain disabled unless explicitly enabled with protections.
- Projectile entities must be cleaned up on hit, timeout, player disconnect or plugin disable.
- Rate-limit firing and validate ownership to prevent projectile spoofing.
- Add world/region protection hooks so weapons can be disabled in spawn, safe zones or protected structures.
- Weapon durability, repair and maintenance are planned; do not assume unlimited lifetime for the final server economy.

## Loot distribution principles

Loot is themed by structure identity and condition. A building may be stripped, normally abandoned or unusually sealed. Sealed examples are rare and should not guarantee exceptional rewards.

Initial conceptual distribution:
- Suburban houses: household salvage and basic supplies.
- Police stations: ordinary supplies, a chance of basic firearms and tear gas.
- Hunting cabins: hunting-themed equipment, rifles or shotguns.
- Military checkpoints: basic military equipment, but often depleted.
- Military bunkers: better weapon chances, rare ammunition and exceptional finds at very low rates.
- Laboratories: rare technical components and unusual materials.
- Offices and skyscrapers: paper, books, household remnants and occasional valuables.
- Industrial sites: tools, metal salvage and components.

Container contents are generated once per structure/container identity. Reopening, unloading or reloading a chunk must not reroll loot. Loot must not refill.
