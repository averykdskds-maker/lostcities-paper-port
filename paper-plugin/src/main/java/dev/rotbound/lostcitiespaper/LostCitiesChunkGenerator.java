package dev.rotbound.lostcitiespaper;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public final class LostCitiesChunkGenerator extends ChunkGenerator {
    private final LostCitiesPaperPlugin plugin;
    private final AssetPack assets;
    private final double cityChance;
    private final int regionBlocks;
    private final int centerJitter;
    private final int minRadius;
    private final int maxRadius;
    private final int streetSpacing;
    private final int groundLevel;
    private final int minFloors;
    private final int maxFloors;
    private final int terrainBlendChunks;

    public LostCitiesChunkGenerator(LostCitiesPaperPlugin plugin, AssetPack assets) {
        this.plugin = plugin;
        this.assets = assets;
        cityChance = clamp(plugin.getConfig().getDouble("city-chance", 1.0), 0.0, 1.0);
        int configuredRegion = plugin.getConfig().getInt("city-region-blocks", 768);
        regionBlocks = Math.max(256, (configuredRegion / 16) * 16);
        centerJitter = Math.max(0, Math.min(regionBlocks / 8, plugin.getConfig().getInt("city-center-jitter-blocks", 32)));
        minRadius = Math.max(64, plugin.getConfig().getInt("city-radius-min-blocks", 176));
        maxRadius = Math.max(minRadius, plugin.getConfig().getInt("city-radius-max-blocks", 270));
        streetSpacing = Math.max(2, plugin.getConfig().getInt("street-spacing-chunks", 4));
        groundLevel = plugin.getConfig().getInt("ground-level", 71);
        minFloors = Math.max(1, plugin.getConfig().getInt("building-floors-min", 2));
        maxFloors = Math.max(minFloors, plugin.getConfig().getInt("building-floors-max", 6));
        terrainBlendChunks = Math.max(0, Math.min(8, plugin.getConfig().getInt("terrain-blend-chunks", 3)));
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return cityAtChunk(worldInfo.getSeed(), chunkX, chunkZ) == null;
    }

    @Override
    public boolean shouldGenerateDecorations(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return cityAtChunk(worldInfo.getSeed(), chunkX, chunkZ) == null;
    }

    @Override
    public boolean shouldGenerateStructures(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return cityAtChunk(worldInfo.getSeed(), chunkX, chunkZ) == null;
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        CityPlan city = cityAtChunk(worldInfo.getSeed(), chunkX, chunkZ);
        if (city == null) {
            CityPlan edge = cityTransitionAtChunk(worldInfo.getSeed(), chunkX, chunkZ);
            if (edge != null) {
                blendTerrainToCity(chunkData, edge, chunkX, chunkZ);
            }
            return;
        }
        if (groundLevel <= chunkData.getMinHeight() + 8 || groundLevel >= chunkData.getMaxHeight() - 64) {
            return;
        }

        Random chunkRandom = new Random(mix(worldInfo.getSeed(), chunkX, chunkZ));
        AssetPack.Palette defaultPalette = assets.standardPalette(chunkRandom);
        flattenUrbanChunk(chunkData);

        int offsetX = chunkX - city.centerChunkX;
        int offsetZ = chunkZ - city.centerChunkZ;
        boolean roadX = Math.floorMod(offsetX, streetSpacing) == 0;
        boolean roadZ = Math.floorMod(offsetZ, streetSpacing) == 0;

        if (roadX || roadZ) {
            String streetType;
            int rotation;
            if (roadX && roadZ) {
                streetType = "all";
                rotation = 0;
            } else if (roadX) {
                // A fixed-X road runs north/south. The base straight template runs east/west.
                boolean openToNegativeZ = cityAtChunk(worldInfo.getSeed(), chunkX, chunkZ - 1) != null;
                boolean openToPositiveZ = cityAtChunk(worldInfo.getSeed(), chunkX, chunkZ + 1) != null;
                streetType = openToNegativeZ && openToPositiveZ ? "straight" : "end";
                rotation = !openToNegativeZ ? 3 : 1;
            } else {
                // A fixed-Z road already runs east/west in the base template.
                boolean openToNegativeX = cityAtChunk(worldInfo.getSeed(), chunkX - 1, chunkZ) != null;
                boolean openToPositiveX = cityAtChunk(worldInfo.getSeed(), chunkX + 1, chunkZ) != null;
                streetType = openToNegativeX && openToPositiveX ? "straight" : "end";
                rotation = !openToNegativeX ? 2 : 0;
            }

            String streetReference = assets.chooseStreet(streetType, chunkRandom);
            AssetPack.Part street = assets.part(streetReference, defaultPalette, chunkRandom);
            if (street == null) {
                street = assets.part("lcmt:street/street_straight_base", defaultPalette, chunkRandom);
            }
            if (street != null) {
                placePart(street, chunkData, 0, groundLevel + 1, 0, rotation, chunkRandom);
            }
            return;
        }

        generateBuilding(chunkData, defaultPalette, chunkRandom);
    }

    private void flattenUrbanChunk(ChunkData data) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = data.getHeight(HeightMap.WORLD_SURFACE_WG, x, z) - 1;
                top = Math.max(data.getMinHeight(), Math.min(data.getMaxHeight() - 1, top));

                if (top > groundLevel) {
                    for (int y = groundLevel + 1; y <= top; y++) {
                        data.setBlock(x, y, z, Material.AIR);
                    }
                } else if (top < groundLevel) {
                    for (int y = top + 1; y < groundLevel; y++) {
                        data.setBlock(x, y, z, Material.STONE);
                    }
                }
                data.setBlock(x, groundLevel, z, Material.STONE);
            }
        }
    }

    /*
     * A narrow, chunk-based grade transition avoids a sheer vertical cut where the
     * flat city grid meets untouched vanilla hills and mountains. The last ring
     * keeps the original vanilla surface height, so terrain outside it is unchanged.
     */
    private void blendTerrainToCity(ChunkData data, CityPlan city, int chunkX, int chunkZ) {
        int distance = cityDistanceChunks(city, chunkX, chunkZ);
        int ring = distance - city.radiusChunks;
        if (ring < 0 || ring >= terrainBlendChunks) {
            return;
        }
        double blend = (ring + 1.0) / terrainBlendChunks;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = data.getHeight(HeightMap.WORLD_SURFACE_WG, x, z) - 1;
                if (top < data.getMinHeight() || top >= data.getMaxHeight()) {
                    continue;
                }

                BlockData surface = data.getBlockData(x, top, z);
                if (surface == null || surface.getMaterial().isAir()) {
                    continue;
                }

                int target = (int) Math.round(groundLevel + (top - groundLevel) * blend);
                target = Math.max(data.getMinHeight() + 1, Math.min(data.getMaxHeight() - 2, target));

                if (target > top) {
                    for (int y = top + 1; y < target; y++) {
                        data.setBlock(x, y, z, Material.STONE);
                    }
                } else if (target < top) {
                    for (int y = target + 1; y <= top; y++) {
                        data.setBlock(x, y, z, Material.AIR);
                    }
                }
                data.setBlock(x, target, z, surface);
            }
        }
    }

    private void generateBuilding(ChunkData data, AssetPack.Palette defaultPalette, Random random) {
        String buildingReference = assets.chooseBuilding(random);
        AssetPack.BuildingDefinition building = assets.building(buildingReference);
        if (building.floorParts().isEmpty()) {
            buildingReference = "lcmt:building1";
            building = assets.building(buildingReference);
        }
        if (building.floorParts().isEmpty()) {
            return;
        }

        int floors = minFloors + random.nextInt(maxFloors - minFloors + 1);
        int y = groundLevel + 1;
        int rotation = random.nextInt(4);
        for (int floor = 0; floor < floors; floor++) {
            String partReference = building.floorParts().get(random.nextInt(building.floorParts().size()));
            AssetPack.Part part = assets.part(partReference, defaultPalette, random);
            if (part == null) {
                continue;
            }
            placePart(part, data, 0, y, 0, rotation, random);
            y += part.slices().size();
        }

        if (!building.topParts().isEmpty()) {
            String topReference = building.topParts().get(random.nextInt(building.topParts().size()));
            AssetPack.Part top = assets.part(topReference, defaultPalette, random);
            if (top != null) {
                placePart(top, data, 0, y, 0, rotation, random);
            }
        }
    }

    private void placePart(AssetPack.Part part, ChunkData data, int originX, int originY, int originZ,
                           int rotation, Random random) {
        for (int sy = 0; sy < part.slices().size(); sy++) {
            int y = originY + sy;
            if (y < data.getMinHeight() || y >= data.getMaxHeight()) {
                continue;
            }
            String slice = part.slices().get(sy);
            for (int z = 0; z < part.zSize(); z++) {
                for (int x = 0; x < part.xSize(); x++) {
                    int index = z * part.xSize() + x;
                    if (index >= slice.length()) {
                        continue;
                    }
                    char symbol = slice.charAt(index);
                    int targetX;
                    int targetZ;
                    switch (Math.floorMod(rotation, 4)) {
                        case 1 -> { targetX = part.zSize() - 1 - z; targetZ = x; }
                        case 2 -> { targetX = part.xSize() - 1 - x; targetZ = part.zSize() - 1 - z; }
                        case 3 -> { targetX = z; targetZ = part.xSize() - 1 - x; }
                        default -> { targetX = x; targetZ = z; }
                    }
                    targetX += originX;
                    targetZ += originZ;
                    if (targetX < 0 || targetX >= 16 || targetZ < 0 || targetZ >= 16) {
                        continue;
                    }

                    if (symbol == '~') {
                        continue;
                    }

                    BlockData block = part.palette().resolve(String.valueOf(symbol), random, assets);
                    if (block == null && symbol == '#') {
                        block = assets.blockData("minecraft:stone_bricks");
                    } else if (block == null && symbol == '}') {
                        block = assets.blockData("minecraft:coarse_dirt");
                    }
                    if (block == null || block.getMaterial() == Material.STRUCTURE_VOID) {
                        continue;
                    }
                    data.setBlock(targetX, y, targetZ, block);
                }
            }
        }
    }

    private CityPlan cityAtChunk(long worldSeed, int chunkX, int chunkZ) {
        CityPlan plan = planForChunkRegion(worldSeed, chunkX, chunkZ);
        return plan != null && cityDistanceChunks(plan, chunkX, chunkZ) < plan.radiusChunks ? plan : null;
    }

    private CityPlan cityTransitionAtChunk(long worldSeed, int chunkX, int chunkZ) {
        if (terrainBlendChunks == 0) {
            return null;
        }
        CityPlan plan = planForChunkRegion(worldSeed, chunkX, chunkZ);
        if (plan == null) {
            return null;
        }
        int distance = cityDistanceChunks(plan, chunkX, chunkZ);
        return distance >= plan.radiusChunks && distance < plan.radiusChunks + terrainBlendChunks ? plan : null;
    }

    private CityPlan planForChunkRegion(long worldSeed, int chunkX, int chunkZ) {
        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;
        int regionX = Math.floorDiv(blockX, regionBlocks);
        int regionZ = Math.floorDiv(blockZ, regionBlocks);
        return cityForRegion(worldSeed, regionX, regionZ);
    }

    private int cityDistanceChunks(CityPlan plan, int chunkX, int chunkZ) {
        int dx = Math.abs(chunkX - plan.centerChunkX);
        int dz = Math.abs(chunkZ - plan.centerChunkZ);
        return Math.max(dx, dz);
    }

    private CityPlan cityForRegion(long seed, int regionX, int regionZ) {
        Random random = new Random(mix(seed, regionX * 73428767, regionZ * 912931));
        if (random.nextDouble() > cityChance) {
            return null;
        }
        int centerX = regionX * regionBlocks + regionBlocks / 2 + random.nextInt(centerJitter * 2 + 1) - centerJitter;
        int centerZ = regionZ * regionBlocks + regionBlocks / 2 + random.nextInt(centerJitter * 2 + 1) - centerJitter;
        centerX = Math.floorDiv(centerX, 16) * 16;
        centerZ = Math.floorDiv(centerZ, 16) * 16;
        int requestedRadius = minRadius + random.nextInt(maxRadius - minRadius + 1);
        int requestedChunks = Math.max(streetSpacing, (requestedRadius + 15) / 16);
        int radiusChunks = ((requestedChunks + streetSpacing - 1) / streetSpacing) * streetSpacing;
        return new CityPlan(centerX, centerZ, Math.floorDiv(centerX, 16), Math.floorDiv(centerZ, 16), radiusChunks);
    }

    public Location suggestedSpawn(World world) {
        CityPlan plan = cityForRegion(world.getSeed(), 0, 0);
        if (plan == null) {
            plan = cityForRegion(world.getSeed(), 1, 0);
        }
        if (plan == null) {
            return new Location(world, 8.5, groundLevel + 2, 8.5);
        }
        return new Location(world, plan.centerBlockX + 8.5, groundLevel + 2, plan.centerBlockZ + 8.5);
    }

    private static long mix(long seed, int x, int z) {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value = Long.rotateLeft(value, 27);
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record CityPlan(int centerBlockX, int centerBlockZ, int centerChunkX, int centerChunkZ,
                            int radiusChunks) {
    }
}
