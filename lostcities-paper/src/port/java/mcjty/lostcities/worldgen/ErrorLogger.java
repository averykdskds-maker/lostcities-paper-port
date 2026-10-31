package mcjty.lostcities.worldgen;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.api.LostChunkCharacteristics;
import mcjty.lostcities.varia.ChunkCoord;
import mcjty.lostcities.worldgen.lost.BuildingInfo;

public final class ErrorLogger {
    private static volatile long lastReportTime = -1;
    private ErrorLogger() {}

    public static void report(String message) {
        long now = System.currentTimeMillis();
        if (lastReportTime == -1 || lastReportTime < now - 10_000) {
            lastReportTime = now;
            LostCities.getLogger().error(message);
        }
    }

    public static void logChunkInfo(int chunkX, int chunkZ, IDimensionInfo provider) {
        var logger = LostCities.getLogger();
        try {
            ChunkCoord coord = new ChunkCoord(provider.getType(), chunkX, chunkZ);
            logger.info("IsCity: {}", BuildingInfo.isCityRaw(coord, provider, provider.getProfile()));
            LostChunkCharacteristics characteristics = BuildingInfo.getChunkCharacteristics(coord, provider);
            logger.info("    Level: {}", characteristics.cityLevel);
            if (characteristics.multiBuilding != null) logger.info("    Multibuilding: {}", characteristics.multiBuilding.getName());
            if (characteristics.buildingType != null) logger.info("    Building: {}", characteristics.buildingType.getName());
            BuildingInfo info = BuildingInfo.getBuildingInfo(coord, provider);
            if (info.hasBuilding) {
                logger.info("        Floors: {}", info.getNumFloors());
                logger.info("        Cellars: {}", info.getNumCellars());
            }
        } catch (Exception e) {
            logger.warn("Error logging chunk info!", e);
        }
    }
}
