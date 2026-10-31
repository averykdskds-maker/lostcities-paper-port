package mcjty.lostcities.worldgen;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.config.ProfileSetup;
import mcjty.lostcities.setup.Config;
import mcjty.lostcities.worldgen.lost.cityassets.AssetRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeTags;
import net.minecraft.world.level.chunk.ChunkAccess;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Paper adapter for the upstream Lost Cities feature lifecycle. The actual terrain,
 * building, highway, railway, bridge and damage generation remains in the upstream
 * LostCityTerrainFeature implementation.
 */
public final class LostCityFeature {
    private final Map<ResourceKey<Level>, IDimensionInfo> dimensionInfo = new ConcurrentHashMap<>();
    private static final int GENERATION_LOCK_STRIPES = 4096;
    private final ReentrantLock[] generationLocks = createGenerationLocks();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    public static volatile int globalDimensionInfoDirtyCounter = 0;
    private int dimensionInfoDirtyCounter = -1;

    public boolean generate(WorldGenRegion region, int chunkX, int chunkZ) {
        return runWithDimensionInfo(region, diminfo -> {
            ChunkPos center = region.getCenter();
            Holder<Biome> biome = region.getBiome(center.getMiddleBlockPosition(60));
            if (biome.is(BiomeTags.IS_VOID)) return false;
            try {
                diminfo.getFeature().generate(region, region.getChunk(chunkX, chunkZ));
            } catch (Exception e) {
                LostCities.getLogger().error("Error generating chunk {},{}: {}", chunkX, chunkZ, e.getMessage(), e);
                e.printStackTrace();
                ErrorLogger.logChunkInfo(chunkX, chunkZ, diminfo);
                ErrorLogger.report("There was an error generating a Lost Cities chunk. See log for details!");
            }
            return true;
        });
    }

    public boolean runWithDimensionInfo(WorldGenLevel world, GenerationAction action) {
        lockCurrentLifecycleForReading();
        try {
            IDimensionInfo diminfo = getOrCreateDimensionInfo(world);
            if (diminfo == null) return false;
            if (world instanceof WorldGenRegion region) {
                ChunkPos center = region.getCenter();
                return runWithChunkNeighborhoodLocks(world.getLevel().dimension(), center.x(), center.z(),
                        () -> action.generate(diminfo));
            }
            return action.generate(diminfo);
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @FunctionalInterface
    public interface GenerationAction {
        boolean generate(IDimensionInfo dimensionInfo);
    }

    private boolean runWithChunkNeighborhoodLocks(ResourceKey<Level> dimension, int chunkX, int chunkZ,
                                                  GenerationActionWithoutInfo action) {
        int[] stripes = new int[9];
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int stripe = getGenerationLockStripe(dimension, chunkX + dx, chunkZ + dz);
                boolean duplicate = false;
                for (int i = 0; i < count; i++) if (stripes[i] == stripe) { duplicate = true; break; }
                if (!duplicate) stripes[count++] = stripe;
            }
        }
        Arrays.sort(stripes, 0, count);
        for (int i = 0; i < count; i++) generationLocks[stripes[i]].lock();
        try {
            return action.generate();
        } finally {
            for (int i = count - 1; i >= 0; i--) generationLocks[stripes[i]].unlock();
        }
    }

    private static int getGenerationLockStripe(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        int hash = dimension.identifier().hashCode();
        hash = 31 * hash + chunkX;
        hash = 31 * hash + chunkZ;
        hash ^= hash >>> 16;
        return hash & (GENERATION_LOCK_STRIPES - 1);
    }

    private static ReentrantLock[] createGenerationLocks() {
        ReentrantLock[] locks = new ReentrantLock[GENERATION_LOCK_STRIPES];
        Arrays.setAll(locks, ignored -> new ReentrantLock());
        return locks;
    }

    @FunctionalInterface
    private interface GenerationActionWithoutInfo {
        boolean generate();
    }

    @Nullable
    public IDimensionInfo getDimensionInfo(WorldGenLevel world) {
        lockCurrentLifecycleForReading();
        try {
            return getOrCreateDimensionInfo(world);
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private void lockCurrentLifecycleForReading() {
        while (true) {
            lifecycleLock.readLock().lock();
            if (globalDimensionInfoDirtyCounter == dimensionInfoDirtyCounter) return;
            lifecycleLock.readLock().unlock();
            lifecycleLock.writeLock().lock();
            try {
                if (globalDimensionInfoDirtyCounter != dimensionInfoDirtyCounter) cleanUpInternal();
            } finally {
                lifecycleLock.writeLock().unlock();
            }
        }
    }

    @Nullable
    private IDimensionInfo getOrCreateDimensionInfo(WorldGenLevel world) {
        ResourceKey<Level> type = world.getLevel().dimension();
        String profileName = Config.getProfileForDimension(world.getLevel(), type);
        if (profileName == null) return null;
        LostCityProfile profile = ProfileSetup.STANDARD_PROFILES.get(profileName);
        if (profile == null) {
            LostCities.getLogger().error("Unknown Lost Cities profile '{}' for dimension {}", profileName, type.identifier());
            return null;
        }
        AssetRegistries.load(world);
        IDimensionInfo info = dimensionInfo.computeIfAbsent(type, key -> {
            LostCityProfile outsideProfile = profile.CITYSPHERE_OUTSIDE_PROFILE == null
                    ? null : ProfileSetup.STANDARD_PROFILES.get(profile.CITYSPHERE_OUTSIDE_PROFILE);
            return new DefaultDimensionInfo(world, profile, outsideProfile);
        });
        info.setWorld(world);
        return info;
    }

    public void cleanUp() {
        lifecycleLock.writeLock().lock();
        try {
            cleanUpInternal();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void cleanUpInternal() {
        LostCities.lostCitiesImp.cleanUp();
        AssetRegistries.reset();
        dimensionInfo.values().forEach(IDimensionInfo::shutdown);
        dimensionInfo.clear();
        dimensionInfoDirtyCounter = globalDimensionInfoDirtyCounter;
    }
}
