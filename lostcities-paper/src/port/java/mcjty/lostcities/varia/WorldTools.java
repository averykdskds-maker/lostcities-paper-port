package mcjty.lostcities.varia;

import it.unimi.dsi.fastutil.longs.LongSet;
import mcjty.lostcities.LostCities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;

public class WorldTools {
    public static boolean chunkLoaded(Level world, BlockPos pos) {
        return world != null && pos != null && world.hasChunkAt(pos);
    }

    public static ServerLevel getOverworld() {
        MinecraftServer server = LostCities.getServer();
        return server == null ? null : server.getLevel(Level.OVERWORLD);
    }

    public static ServerLevel getOverworld(Level world) {
        return world == null || world.getServer() == null ? null : world.getServer().getLevel(Level.OVERWORLD);
    }

    public static ServerLevel loadWorld(net.minecraft.resources.ResourceKey<Level> type) {
        return getWorld(type);
    }

    public static ServerLevel getWorld(net.minecraft.resources.ResourceKey<Level> type) {
        MinecraftServer server = LostCities.getServer();
        return server == null ? null : server.getLevel(type);
    }

    public static ServerLevel getWorld(Level world, net.minecraft.resources.ResourceKey<Level> type) {
        return world == null || world.getServer() == null ? null : world.getServer().getLevel(type);
    }

    public static Map<Structure, LongSet> checkStructures(ServerLevel level, ChunkCoord coord) {
        BlockPos center = new BlockPos((coord.chunkX() << 4) + 8, 60, (coord.chunkZ() << 4) + 8);
        StructureManager manager = level.structureManager();
        return manager.getAllStructuresAt(center);
    }
}
