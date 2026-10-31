package mcjty.lostcities;

import mcjty.lostcities.api.ILostCities;
import mcjty.lostcities.api.ILostCityAssetRegistry;
import mcjty.lostcities.api.ILostCityBuilding;
import mcjty.lostcities.api.ILostCityCityStyle;
import mcjty.lostcities.api.ILostCityInformation;
import mcjty.lostcities.api.ILostCityMultiBuilding;
import mcjty.lostcities.api.ILostChunkInfo;
import mcjty.lostcities.api.ILostSphere;
import mcjty.lostcities.setup.Config;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import mcjty.lostcities.worldgen.lost.cityassets.AssetRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LostCitiesImp implements ILostCities {
    private final Map<ResourceKey<Level>, LostCityInformation> info = new ConcurrentHashMap<>();

    public void cleanUp() {
        info.clear();
    }

    @Nullable
    @Override
    public ILostCityInformation getLostInfo(Level world) {
        if (!(world instanceof WorldGenLevel genLevel)) return null;
        IDimensionInfo dimensionInfo = LostCities.LOSTCITY_FEATURE.getDimensionInfo(genLevel);
        if (dimensionInfo == null) return null;
        return info.computeIfAbsent(world.dimension(), key -> new LostCityInformation(dimensionInfo));
    }

    @Override
    public void registerDimension(ServerLevel level, ResourceKey<Level> key, String profile) {
        Config.registerLostCityDimension(level, key, profile);
    }

    @Override
    public void setOverworldProfile(String profile) {
        Config.SELECTED_PROFILE.set(profile);
        Config.resetProfileCache();
        LostCities.LOSTCITY_FEATURE.cleanUp();
    }

    public record LostCityInformation(IDimensionInfo dimensionInfo) implements ILostCityInformation {
        @Override
        public ILostChunkInfo getChunkInfo(int chunkX, int chunkZ) {
            ChunkCoord coord = new ChunkCoord(dimensionInfo.getType(), chunkX, chunkZ);
            return BuildingInfo.getBuildingInfo(coord, dimensionInfo);
        }

        @Override
        public ILostSphere getSphere(int x, int y, int z) {
            if (dimensionInfo.getProfile().isSpheres() || dimensionInfo.getProfile().isSpace()) {
                return BuildingInfo.getSphereInt(x, y, z, dimensionInfo);
            }
            return null;
        }

        @Override
        public ILostSphere getSphere(int x, int z) {
            if (dimensionInfo.getProfile().isSpheres() || dimensionInfo.getProfile().isSpace()) {
                return BuildingInfo.getSphereInt(x, z, dimensionInfo);
            }
            return null;
        }

        @Override
        public int getRealHeight(int level) {
            return dimensionInfo.getProfile().GROUNDLEVEL + level * 6;
        }

        @Override
        public ILostCityAssetRegistry<ILostCityBuilding> getBuildings() {
            return AssetRegistries.BUILDINGS.cast();
        }

        @Override
        public ILostCityAssetRegistry<ILostCityMultiBuilding> getMultiBuildings() {
            return AssetRegistries.MULTI_BUILDINGS.cast();
        }

        @Override
        public ILostCityAssetRegistry<ILostCityCityStyle> getCityStyles() {
            return AssetRegistries.CITYSTYLES.cast();
        }
    }
}
