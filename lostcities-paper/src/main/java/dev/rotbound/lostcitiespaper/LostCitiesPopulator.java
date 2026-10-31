package dev.rotbound.lostcitiespaper;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.bukkit.craftbukkit.generator.CraftLimitedRegion;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LostCitiesPopulator extends BlockPopulator {
    private final LostCitiesPaperPlugin plugin;
    private final AtomicBoolean integrationReported = new AtomicBoolean();

    public LostCitiesPopulator(LostCitiesPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
        if (!(limitedRegion instanceof CraftLimitedRegion craftRegion)) {
            if (integrationReported.compareAndSet(false, true)) {
                plugin.getLogger().severe("Paper supplied an unknown LimitedRegion implementation: "
                        + limitedRegion.getClass().getName());
            }
            return;
        }
        WorldGenLevel level = craftRegion.getHandle();
        if (!(level instanceof WorldGenRegion region)) {
            if (integrationReported.compareAndSet(false, true)) {
                plugin.getLogger().severe("Expected WorldGenRegion at FEATURES stage but received "
                        + level.getClass().getName());
            }
            return;
        }
        ChunkAccess chunk = region.getChunk(chunkX, chunkZ);
        if (integrationReported.compareAndSet(false, true)) {
            plugin.getLogger().info("Paper NMS bridge verified: BlockPopulator received "
                    + region.getClass().getSimpleName() + " and chunk " + chunk.getPos() + ".");
        }
        // The upstream LostCityTerrainFeature.generate(region, chunk) call is connected here
        // after its NeoForge bootstrap/config/asset dependencies have been ported.
    }
}
