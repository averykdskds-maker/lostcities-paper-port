package dev.rotbound.lostcitiespaper;

import mcjty.lostcities.LostCities;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import org.bukkit.craftbukkit.generator.CraftLimitedRegion;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hooks the upstream Lost Cities generator into Paper's FEATURES stage. The original
 * LostCityTerrainFeature implementation writes to Paper's live WorldGenRegion/ChunkAccess.
 */
public final class LostCitiesPopulator extends BlockPopulator {
    private final LostCitiesPaperPlugin plugin;
    private final AtomicBoolean integrationReported = new AtomicBoolean();
    private final AtomicInteger generationCallbacksReported = new AtomicInteger();

    public LostCitiesPopulator(LostCitiesPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
        if (!(limitedRegion instanceof CraftLimitedRegion craftRegion)) {
            if (integrationReported.compareAndSet(false, true)) {
                plugin.getLogger().severe("Unsupported Paper LimitedRegion: " + limitedRegion.getClass().getName());
            }
            return;
        }
        WorldGenLevel level = craftRegion.getHandle();
        if (!(level instanceof WorldGenRegion region)) {
            if (integrationReported.compareAndSet(false, true)) {
                plugin.getLogger().severe("Expected WorldGenRegion during FEATURES, received " + level.getClass().getName());
            }
            return;
        }
        if (integrationReported.compareAndSet(false, true)) {
            plugin.getLogger().info("Connected original Lost Cities engine to Paper FEATURES-stage WorldGenRegion.");
        }

        // Keep a small startup trace, and always report the distant sentinel chunk used by CI.
        // This lets the runtime smoke test distinguish a real second generation callback from
        // merely issuing a forceload command successfully.
        int callbackNumber = generationCallbacksReported.getAndIncrement();
        if (callbackNumber < 3 || (chunkX == 100 && chunkZ == 100)) {
            plugin.getLogger().info("Lost Cities FEATURES generation entered for chunk " + chunkX + "," + chunkZ
                    + " in world '" + worldInfo.getName() + "'.");
        }
        LostCities.LOSTCITY_FEATURE.generate(region, chunkX, chunkZ);
    }
}
