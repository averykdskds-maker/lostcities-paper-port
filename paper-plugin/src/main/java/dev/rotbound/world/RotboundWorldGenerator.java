package dev.rotbound.world;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RotboundWorldGenerator extends ChunkGenerator {
    private final int seaLevel;
    private final double density;
    private final SettlementPlanner planner = new SettlementPlanner();

    public RotboundWorldGenerator(int seaLevel, double density) {
        this.seaLevel = seaLevel;
        this.density = Math.max(.35, Math.min(1.0, density));
    }

    @Override public boolean shouldGenerateSurface() { return true; }
    @Override public boolean shouldGenerateCaves() { return true; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public boolean shouldGenerateDecorations(WorldInfo info, Random random, int chunkX, int chunkZ) {
        List<SettlementPlanner.Settlement> sites = planner.settlementsForChunk(info.getSeed(), chunkX, chunkZ);
        for (SettlementPlanner.Settlement site : sites) {
            if (site.contains(chunkX * 16 + 2, chunkZ * 16 + 2)
                    || site.contains(chunkX * 16 + 13, chunkZ * 16 + 2)
                    || site.contains(chunkX * 16 + 2, chunkZ * 16 + 13)
                    || site.contains(chunkX * 16 + 13, chunkZ * 16 + 13)) return false;
        }
        return true;
    }

    @Override
    public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        long seed = info.getSeed();
        List<SettlementPlanner.Settlement> sites = planner.settlementsForChunk(seed, chunkX, chunkZ);
        List<SettlementPlanner.RoadSegment> highways = planner.highwaysForChunk(seed, chunkX, chunkZ);
        int[][] heights = surfaceHeights(data);
        if (countLandColumns(data, heights) == 0) {
            generateShipwrecks(seed, chunkX, chunkZ, data);
            return;
        }

        drawRoadNetwork(seed, chunkX, chunkZ, data, heights, sites, highways);
        for (SettlementPlanner.Settlement site : sites) {
            drawPlannedBuildings(seed, chunkX, chunkZ, data, heights, site);
            switch (site.type()) {
                case AIRPORT -> drawAirport(seed, chunkX, chunkZ, data, heights, site);
                case PORT -> drawPort(seed, chunkX, chunkZ, data, heights, site);
                case MILITARY_BASE -> drawMilitaryBase(seed, chunkX, chunkZ, data, heights, site);
                case INDUSTRIAL -> drawIndustrialSite(seed, chunkX, chunkZ, data, heights, site);
                default -> { }
            }
        }
        drawHighwayWrecks(seed, chunkX, chunkZ, data, heights, highways);
        generateShipwrecks(seed, chunkX, chunkZ, data);
    }

    private int[][] surfaceHeights(ChunkData data) {
        int[][] heights = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                heights[x][z] = Math.max(data.getMinHeight(),
                        Math.min(data.getMaxHeight() - 2,
                                data.getHeight(HeightMap.WORLD_SURFACE_WG, x, z) - 1));
            }
        }
        return heights;
    }

    private int countLandColumns(ChunkData data, int[][] heights) {
        int count = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            if (!isOcean(data, x, heights[x][z], z)) count++;
        }
        return count;
    }

    private boolean isOcean(ChunkData data, int x, int y, int z) {
        return data.getBiome(x, Math.max(data.getMinHeight(), Math.min(data.getMaxHeight() - 1, y)), z)
                .name().contains("OCEAN");
    }

    private void drawRoadNetwork(long seed, int cx, int cz, ChunkData data, int[][] heights,
                                 List<SettlementPlanner.Settlement> sites,
                                 List<SettlementPlanner.RoadSegment> highways) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = cx * 16 + x, wz = cz * 16 + z, y = heights[x][z];
                if (isOcean(data, x, y, z)) continue;
                SettlementPlanner.Settlement roadSite = null;
                boolean runway = false;
                for (SettlementPlanner.Settlement site : sites) {
                    if (!site.contains(wx, wz)) continue;
                    if (site.isRunway(wx, wz)) { runway = true; roadSite = site; }
                    else if (site.isRoad(wx, wz)) roadSite = site;
                }
                boolean highway = onHighway(wx, wz, highways);
                if (roadSite == null && !highway) continue;
                long h = SettlementPlanner.mix(seed ^ (roadSite == null ? 0 : roadSite.id()), wx, wz);
                Material surface;
                if (runway) {
                    surface = Math.floorMod(wx + wz, 31) < 2 ? Material.WHITE_CONCRETE : Material.GRAY_CONCRETE;
                } else if (highway) {
                    surface = Math.floorMod(h, 100) < 68 ? Material.BLACK_CONCRETE
                            : (Math.floorMod(h, 100) < 85 ? Material.GRAY_CONCRETE : Material.CRACKED_STONE_BRICKS);
                } else {
                    int n = Math.floorMod(h, 100);
                    surface = n < 32 ? Material.GRAY_CONCRETE
                            : n < 52 ? Material.CRACKED_STONE_BRICKS
                            : n < 70 ? Material.ANDESITE
                            : n < 83 ? Material.MOSSY_COBBLESTONE
                            : n < 94 ? Material.COARSE_DIRT : Material.MOSS_BLOCK;
                }
                data.setBlock(x, y, z, surface);
                if (highway && Math.floorMod(wx + wz, 13) < 2) {
                    if (y + 1 < data.getMaxHeight()) data.setBlock(x, y + 1, z, Material.AIR);
                }
            }
        }
    }

    private boolean onHighway(double x, double z, List<SettlementPlanner.RoadSegment> segments) {
        for (SettlementPlanner.RoadSegment segment : segments) {
            if (segment.distanceSquared(x, z) <= 7.5 * 7.5) return true;
        }
        return false;
    }

    private void drawPlannedBuildings(long seed, int cx, int cz, ChunkData data, int[][] heights,
                                      SettlementPlanner.Settlement site) {
        double[][] corners = {
                site.toLocal(cx * 16, cz * 16),
                site.toLocal(cx * 16 + 15, cz * 16),
                site.toLocal(cx * 16, cz * 16 + 15),
                site.toLocal(cx * 16 + 15, cz * 16 + 15)
        };
        double minU = Double.MAX_VALUE, maxU = -Double.MAX_VALUE;
        double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (double[] p : corners) {
            minU = Math.min(minU, p[0]); maxU = Math.max(maxU, p[0]);
            minV = Math.min(minV, p[1]); maxV = Math.max(maxV, p[1]);
        }
        int minCellU = (int) Math.floor(minU / SettlementPlanner.PLOT_BLOCKS) - 1;
        int maxCellU = (int) Math.floor(maxU / SettlementPlanner.PLOT_BLOCKS) + 1;
        int minCellV = (int) Math.floor(minV / SettlementPlanner.PLOT_BLOCKS) - 1;
        int maxCellV = (int) Math.floor(maxV / SettlementPlanner.PLOT_BLOCKS) + 1;

        for (int cellU = minCellU; cellU <= maxCellU; cellU++) {
            for (int cellV = minCellV; cellV <= maxCellV; cellV++) {
                double u = cellU * SettlementPlanner.PLOT_BLOCKS + SettlementPlanner.PLOT_BLOCKS / 2.0;
                double v = cellV * SettlementPlanner.PLOT_BLOCKS + SettlementPlanner.PLOT_BLOCKS / 2.0;
                int[] world = site.toWorld(u, v);
                if (!site.contains(world[0], world[1]) || site.isRoad(world[0], world[1])) continue;
                if (site.type() == SettlementPlanner.Type.AIRPORT || site.type() == SettlementPlanner.Type.PORT) continue;
                Random r = new Random(SettlementPlanner.mix(seed ^ site.id(), cellU, cellV));
                double urban = site.normalizedDistance(world[0], world[1]);
                if (r.nextDouble() > buildingChance(site.type(), urban) * density) continue;

                BuildingKind kind = chooseBuildingKind(r, site.type(), urban);
                int width = buildingWidth(site.type(), urban, r);
                int depth = buildingWidth(site.type(), urban, r);
                int height = buildingHeight(site.type(), urban, r);
                drawFootprint(seed, cx, cz, data, heights, site, u, v, width, depth, height, kind,
                        SettlementPlanner.mix(site.id(), cellU, cellV));
            }
        }
    }

    private double buildingChance(SettlementPlanner.Type type, double urban) {
        double central = urban < .28 ? .12 : urban < .62 ? .04 : -.08;
        return switch (type) {
            case METROPOLIS -> .91 + central;
            case CITY -> .88 + central;
            case TOWN -> .82 + central;
            case SUBURB -> .72;
            case VILLAGE -> .58;
            case INDUSTRIAL -> .78;
            case MILITARY_BASE -> .60;
            case RURAL -> .42;
            case AIRPORT, PORT -> .05;
        };
    }

    private int buildingWidth(SettlementPlanner.Type type, double urban, Random r) {
        int low = switch (type) {
            case METROPOLIS, CITY -> urban < .35 ? 38 : 30;
            case TOWN, INDUSTRIAL -> 26;
            case SUBURB, MILITARY_BASE -> 20;
            case VILLAGE, RURAL -> 16;
            default -> 20;
        };
        int high = switch (type) {
            case METROPOLIS, CITY -> urban < .35 ? 58 : 48;
            case TOWN, INDUSTRIAL -> 44;
            case SUBURB, MILITARY_BASE -> 38;
            case VILLAGE, RURAL -> 30;
            default -> 36;
        };
        return r.nextInt(low, high + 1);
    }

    private int buildingHeight(SettlementPlanner.Type type, double urban, Random r) {
        return switch (type) {
            case METROPOLIS -> urban < .28 ? r.nextInt(42, 100) : urban < .62 ? r.nextInt(22, 58) : r.nextInt(10, 32);
            case CITY -> urban < .25 ? r.nextInt(34, 78) : urban < .65 ? r.nextInt(16, 46) : r.nextInt(8, 25);
            case TOWN -> r.nextInt(7, 24);
            case SUBURB -> r.nextInt(4, 12);
            case VILLAGE, RURAL -> r.nextInt(4, 9);
            case INDUSTRIAL -> r.nextInt(8, 25);
            case MILITARY_BASE -> r.nextInt(5, 14);
            case AIRPORT -> r.nextInt(8, 18);
            case PORT -> r.nextInt(6, 16);
        };
    }

    private BuildingKind chooseBuildingKind(Random r, SettlementPlanner.Type type, double urban) {
        if (type == SettlementPlanner.Type.VILLAGE || type == SettlementPlanner.Type.RURAL || type == SettlementPlanner.Type.SUBURB) {
            return r.nextInt(8) == 0 ? BuildingKind.SHOP : BuildingKind.HOUSE;
        }
        int n = r.nextInt(100);
        if (n < 3) return BuildingKind.HOSPITAL;
        if (n < 6) return BuildingKind.POLICE;
        if (n < 10) return BuildingKind.SCHOOL;
        if (n < 13) return BuildingKind.STATION;
        if (n < 18) return BuildingKind.SHOP;
        if (n < 22) return BuildingKind.HOTEL;
        if (n < 29) return BuildingKind.OFFICE;
        if (n < 38) return BuildingKind.WAREHOUSE;
        return urban < .32 ? BuildingKind.APARTMENT : BuildingKind.HOUSE;
    }

    private enum BuildingKind { HOUSE, APARTMENT, OFFICE, HOSPITAL, POLICE, SCHOOL, STATION, SHOP, HOTEL, WAREHOUSE }

    /** A whole plot is planned in world coordinates, so its walls continue cleanly across chunk borders. */
    private void drawFootprint(long seed, int cx, int cz, ChunkData data, int[][] heights,
                               SettlementPlanner.Settlement site, double centerU, double centerV,
                               int width, int depth, int height, BuildingKind kind, long buildingSeed) {
        int[][] corners = {
                site.toWorld(centerU - width / 2.0, centerV - depth / 2.0),
                site.toWorld(centerU + width / 2.0, centerV - depth / 2.0),
                site.toWorld(centerU - width / 2.0, centerV + depth / 2.0),
                site.toWorld(centerU + width / 2.0, centerV + depth / 2.0)
        };
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] p : corners) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]);
        }
        minX = Math.max(minX, cx * 16); maxX = Math.min(maxX, cx * 16 + 15);
        minZ = Math.max(minZ, cz * 16); maxZ = Math.min(maxZ, cz * 16 + 15);
        if (minX > maxX || minZ > maxZ) return;

        Material wall = wallMaterial(site, kind);
        Material floor = floorMaterial(kind);
        Material roof = site.type() == SettlementPlanner.Type.INDUSTRIAL
                ? Material.GRAY_CONCRETE : Material.CRACKED_STONE_BRICKS;
        int halfW = width / 2, halfD = depth / 2;
        int safeHeight = Math.max(3, Math.min(height, data.getMaxHeight() - data.getMinHeight() - 8));

        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                double[] local = site.toLocal(wx, wz);
                double du = Math.abs(local[0] - centerU), dv = Math.abs(local[1] - centerV);
                if (du > halfW || dv > halfD) continue;
                int x = wx - cx * 16, z = wz - cz * 16, ground = heights[x][z];
                if (isOcean(data, x, ground, z) || ground + safeHeight + 2 >= data.getMaxHeight()) continue;
                boolean edge = du >= halfW - 1.0 || dv >= halfD - 1.0;
                int ix = (int) Math.round(local[0] - centerU + halfW);
                int iz = (int) Math.round(local[1] - centerV + halfD);
                long columnHash = SettlementPlanner.mix(buildingSeed, wx, wz);
                int floorOffset = 0;
                for (int y = 1; y <= safeHeight; y++) {
                    boolean roofLevel = y == safeHeight;
                    boolean floorLevel = y == 1 || y % 4 == 0;
                    long h = SettlementPlanner.mix(columnHash, y, (int) buildingSeed);
                    if (roofLevel) {
                        if (Math.floorMod(h, 100) >= 58) data.setBlock(x, ground + y, z, roof);
                        continue;
                    }
                    if (edge) {
                        boolean window = y > 2 && !floorLevel
                                && (Math.floorMod(ix + (int) (buildingSeed & 7), 7) == 0
                                || Math.floorMod(iz + (int) ((buildingSeed >>> 3) & 7), 7) == 0);
                        boolean collapse = Math.floorMod(h, 100) < (y > 5 ? 9 : 3);
                        if (!window && !collapse) data.setBlock(x, ground + y, z, wall);
                    } else if (floorLevel) {
                        if (Math.floorMod(h, 100) > 7) data.setBlock(x, ground + y, z, floor);
                    } else if (Math.floorMod(h, 140) == 0) {
                        data.setBlock(x, ground + y, z, Material.COBBLESTONE);
                    }
                    if (y == 1) floorOffset = ground + y;
                }
                // A few roofs carry a visible remnant of their former use, not intact signage.
                if (kind == BuildingKind.HOSPITAL && du < 3 && dv < 3 && safeHeight > 5) {
                    data.setBlock(x, ground + safeHeight + 1, z, Material.RED_CONCRETE);
                } else if (kind == BuildingKind.POLICE && du < 2 && dv < 2 && safeHeight > 5) {
                    data.setBlock(x, ground + safeHeight + 1, z, Material.BLUE_CONCRETE);
                } else if (kind == BuildingKind.SCHOOL && du < 2 && dv < 2 && safeHeight > 5) {
                    data.setBlock(x, ground + safeHeight + 1, z, Material.YELLOW_TERRACOTTA);
                }
                // Nature has reached some interiors: exposed earth and isolated saplings.
                if (!edge && Math.floorMod(columnHash, 997) < 3 && floorOffset > data.getMinHeight() + 2) {
                    data.setBlock(x, floorOffset + 1, z, Material.DIRT);
                    if (floorOffset + 2 < data.getMaxHeight()) data.setBlock(x, floorOffset + 2, z, Material.OAK_SAPLING);
                }
            }
        }
    }

    private Material wallMaterial(SettlementPlanner.Settlement site, BuildingKind kind) {
        if (kind == BuildingKind.HOSPITAL) return Material.WHITE_CONCRETE;
        if (kind == BuildingKind.POLICE) return Material.LIGHT_BLUE_TERRACOTTA;
        if (kind == BuildingKind.SCHOOL) return Material.YELLOW_TERRACOTTA;
        if (kind == BuildingKind.WAREHOUSE) return Material.GRAY_CONCRETE;
        if (kind == BuildingKind.STATION) return Material.STONE_BRICKS;
        return switch (site.country().architecture()) {
            case "Mediterranean" -> Material.TERRACOTTA;
            case "Eastern" -> Material.CRACKED_STONE_BRICKS;
            case "Coastal" -> Material.LIGHT_GRAY_CONCRETE;
            case "Highland" -> Material.ANDESITE;
            case "Industrial" -> Material.GRAY_CONCRETE;
            default -> Material.STONE_BRICKS;
        };
    }

    private Material floorMaterial(BuildingKind kind) {
        return switch (kind) {
            case HOSPITAL, POLICE, SCHOOL -> Material.SMOOTH_STONE;
            case WAREHOUSE, STATION -> Material.POLISHED_ANDESITE;
            case HOUSE -> Material.OAK_PLANKS;
            default -> Material.CRACKED_STONE_BRICKS;
        };
    }

    private void drawAirport(long seed, int cx, int cz, ChunkData data, int[][] heights,
                             SettlementPlanner.Settlement site) {
        drawFootprint(seed, cx, cz, data, heights, site, 0, 115, 142, 62, 18,
                BuildingKind.STATION, site.id() ^ 0xA17A);
        drawFootprint(seed, cx, cz, data, heights, site, -190, 135, 66, 52, 12,
                BuildingKind.WAREHOUSE, site.id() ^ 0xA171);
        drawFootprint(seed, cx, cz, data, heights, site, 190, 135, 66, 52, 12,
                BuildingKind.WAREHOUSE, site.id() ^ 0xA172);
        drawTower(seed, cx, cz, data, heights, site, site.rx() * .34, 92, 14, 42);
        drawAirportApron(cx, cz, data, heights, site);
    }

    private void drawAirportApron(int cx, int cz, ChunkData data, int[][] heights,
                                  SettlementPlanner.Settlement site) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = cx * 16 + x, wz = cz * 16 + z;
            double[] p = site.toLocal(wx, wz);
            if (Math.abs(p[1] - 115) < 5 && Math.abs(p[0]) < 290
                    && !isOcean(data, x, heights[x][z], z)) {
                data.setBlock(x, heights[x][z], z, Material.GRAY_CONCRETE);
            }
        }
    }

    private void drawTower(long seed, int cx, int cz, ChunkData data, int[][] heights,
                           SettlementPlanner.Settlement site, double u, double v, int width, int height) {
        drawFootprint(seed, cx, cz, data, heights, site, u, v, width, width, height,
                BuildingKind.OFFICE, site.id() ^ (long) width * 31L ^ height);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = cx * 16 + x, wz = cz * 16 + z;
            double[] p = site.toLocal(wx, wz);
            if (Math.abs(p[0] - u) <= 2 && Math.abs(p[1] - v) <= 2
                    && !isOcean(data, x, heights[x][z], z)) {
                for (int y = 1; y <= height + 8 && heights[x][z] + y < data.getMaxHeight(); y++) {
                    data.setBlock(x, heights[x][z] + y, z, y > height ? Material.IRON_BARS : Material.GRAY_CONCRETE);
                }
            }
        }
    }

    private void drawPort(long seed, int cx, int cz, ChunkData data, int[][] heights,
                          SettlementPlanner.Settlement site) {
        // Long piers extend from the planned port district; only ocean columns receive the deck.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = cx * 16 + x, wz = cz * 16 + z;
            double[] p = site.toLocal(wx, wz);
            boolean pier = (Math.abs(p[0] - 55) < 4 || Math.abs(p[0] + 55) < 4)
                    && p[1] > 80 && p[1] < 430;
            boolean quay = Math.abs(p[1] - 75) < 5 && Math.abs(p[0]) < 210;
            if (!(pier || quay)) continue;
            int ground = heights[x][z];
            if (!isOcean(data, x, ground, z)) {
                if (quay) data.setBlock(x, ground, z, Material.CRACKED_STONE_BRICKS);
                continue;
            }
            int deck = Math.max(data.getMinHeight() + 4, Math.min(seaLevel - 1, data.getMaxHeight() - 8));
            for (int y = deck - 3; y <= deck; y++) {
                data.setBlock(x, y, z, y == deck ? Material.GRAY_CONCRETE : Material.MOSSY_COBBLESTONE);
            }
            if (Math.floorMod(wx + wz, 32) == 0 && deck + 1 < data.getMaxHeight()) {
                data.setBlock(x, deck + 1, z, Material.IRON_BARS);
            }
        }
        drawCrane(seed, cx, cz, data, heights, site, -135, 190);
        drawCrane(seed, cx, cz, data, heights, site, 135, 310);
    }

    private void drawCrane(long seed, int cx, int cz, ChunkData data, int[][] heights,
                           SettlementPlanner.Settlement site, double u, double v) {
        int[] c = site.toWorld(u, v);
        int lx = c[0] - cx * 16, lz = c[1] - cz * 16;
        if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return;
        int base = heights[lx][lz] + 1;
        for (int y = 0; y < 18 && base + y < data.getMaxHeight(); y++) {
            data.setBlock(lx, base + y, lz, y % 3 == 0 ? Material.IRON_BLOCK : Material.CHAIN);
        }
        for (int dx = -8; dx <= 8; dx++) {
            int wx = c[0] + dx, x = wx - cx * 16, z = lz;
            if (x >= 0 && x < 16 && base + 18 < data.getMaxHeight()) {
                data.setBlock(x, base + 18, z, Material.IRON_BLOCK);
            }
        }
    }

    private void drawMilitaryBase(long seed, int cx, int cz, ChunkData data, int[][] heights,
                                  SettlementPlanner.Settlement site) {
        drawFootprint(seed, cx, cz, data, heights, site, -95, -90, 92, 62, 9,
                BuildingKind.WAREHOUSE, site.id() ^ 0xBA5E);
        drawFootprint(seed, cx, cz, data, heights, site, 105, 100, 76, 58, 7,
                BuildingKind.WAREHOUSE, site.id() ^ 0xBA52);
        drawTower(seed, cx, cz, data, heights, site, -260, -260, 8, 17);
        drawTower(seed, cx, cz, data, heights, site, 260, -260, 8, 17);
        drawTower(seed, cx, cz, data, heights, site, -260, 260, 8, 17);
        drawTower(seed, cx, cz, data, heights, site, 260, 260, 8, 17);

        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = cx * 16 + x, wz = cz * 16 + z;
            double[] p = site.toLocal(wx, wz);
            boolean fence = (Math.abs(Math.abs(p[0]) - 310) < 2 && Math.abs(p[1]) < 312)
                    || (Math.abs(Math.abs(p[1]) - 310) < 2 && Math.abs(p[0]) < 312);
            if (!fence || isOcean(data, x, heights[x][z], z)) continue;
            if (Math.abs(p[0]) < 18 && Math.abs(p[1]) > 280) continue;
            int y = heights[x][z] + 1;
            if (y + 3 < data.getMaxHeight()) {
                data.setBlock(x, y, z, Material.IRON_BARS);
                data.setBlock(x, y + 1, z, Material.IRON_BARS);
                if (Math.floorMod(wx + wz, 8) == 0) data.setBlock(x, y + 2, z, Material.IRON_BARS);
            }
        }
        // A powerless communications mast: the structure remains, not its signal.
        drawTower(seed, cx, cz, data, heights, site, 0, 0, 5, 28);
    }

    private void drawIndustrialSite(long seed, int cx, int cz, ChunkData data, int[][] heights,
                                    SettlementPlanner.Settlement site) {
        int[][] positions = {{-175,-145},{175,-145},{-175,145},{175,145}};
        for (int i = 0; i < positions.length; i++) {
            drawSilo(seed, cx, cz, data, heights, site, positions[i][0], positions[i][1], 9 + i % 3, 24 + i * 5);
        }
        drawFootprint(seed, cx, cz, data, heights, site, 0, 0, 145, 80, 13,
                BuildingKind.WAREHOUSE, site.id() ^ 0x1D05);
    }

    private void drawSilo(long seed, int cx, int cz, ChunkData data, int[][] heights,
                          SettlementPlanner.Settlement site, double u, double v, int radius, int height) {
        int[] c = site.toWorld(u, v);
        int minX = Math.max(cx * 16, c[0] - radius), maxX = Math.min(cx * 16 + 15, c[0] + radius);
        int minZ = Math.max(cz * 16, c[1] - radius), maxZ = Math.min(cz * 16 + 15, c[1] + radius);
        for (int wx = minX; wx <= maxX; wx++) for (int wz = minZ; wz <= maxZ; wz++) {
            double[] p = site.toLocal(wx, wz);
            double dx = p[0] - u, dz = p[1] - v;
            if (dx * dx + dz * dz > radius * radius) continue;
            int x = wx - cx * 16, z = wz - cz * 16, ground = heights[x][z];
            if (isOcean(data, x, ground, z)) continue;
            for (int y = 1; y <= height && ground + y < data.getMaxHeight(); y++) {
                if (y == height || dx * dx + dz * dz > (radius - 1) * (radius - 1)) {
                    data.setBlock(x, ground + y, z, Material.GRAY_CONCRETE);
                }
            }
        }
    }

    private void drawHighwayWrecks(long seed, int cx, int cz, ChunkData data, int[][] heights,
                                   List<SettlementPlanner.RoadSegment> highways) {
        if (highways.isEmpty()) return;
        for (int x = 2; x < 14; x++) for (int z = 2; z < 14; z++) {
            int wx = cx * 16 + x, wz = cz * 16 + z;
            if (!onHighway(wx, wz, highways) || isOcean(data, x, heights[x][z], z)) continue;
            long h = SettlementPlanner.mix(seed ^ 0xCA45L, wx, wz);
            if (Math.floorMod(h, 1800) != 0) continue;
            int y = heights[x][z] + 1;
            if (y + 2 >= data.getMaxHeight()) continue;
            // Tiny roofless, glassless vehicle wrecks, deliberately rare.
            data.setBlock(x, y, z, Material.GRAY_CONCRETE);
            data.setBlock(x + 1, y, z, Material.GRAY_CONCRETE);
            data.setBlock(x, y + 1, z, Material.IRON_TRAPDOOR);
            data.setBlock(x + 1, y + 1, z, Material.IRON_TRAPDOOR);
        }
    }

    private void generateShipwrecks(long seed, int cx, int cz, ChunkData data) {
        int rx = Math.floorDiv(cx * 16 + 8, 2048), rz = Math.floorDiv(cz * 16 + 8, 2048);
        for (int az = rz - 1; az <= rz + 1; az++) for (int ax = rx - 1; ax <= rx + 1; ax++) {
            Random r = new Random(SettlementPlanner.mix(seed ^ 0x5F3759DFL, ax, az));
            if (r.nextDouble() > .075) continue;
            int shipX = ax * 2048 + 300 + r.nextInt(1448);
            int shipZ = az * 2048 + 300 + r.nextInt(1448);
            int rotation = r.nextInt(4);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (!data.getBiome(x, Math.max(data.getMinHeight(), Math.min(data.getMaxHeight() - 1, seaLevel)), z)
                        .name().contains("OCEAN")) continue;
                int dx = cx * 16 + x - shipX, dz = cz * 16 + z - shipZ, u, v;
                switch (rotation) {
                    case 1 -> { u = dz; v = -dx; }
                    case 2 -> { u = -dx; v = -dz; }
                    case 3 -> { u = -dz; v = dx; }
                    default -> { u = dx; v = dz; }
                }
                if (Math.abs(u) > 38 || Math.abs(v) > 10 || Math.abs(v) > Math.max(3, 10 - Math.abs(u) / 5)) continue;
                long h = SettlementPlanner.mix(shipX * 31L + shipZ, cx * 16 + x, cz * 16 + z);
                Material hull = Math.floorMod(h, 7) == 0 ? Material.MOSSY_COBBLESTONE : Material.GRAY_CONCRETE;
                for (int y = seaLevel - 4; y <= seaLevel; y++) {
                    if (y < data.getMinHeight() || y >= data.getMaxHeight()) continue;
                    data.setBlock(x, y, z, y == seaLevel && Math.abs(v) <= 8 ? Material.DARK_OAK_PLANKS : hull);
                }
                if (Math.abs(u) < 12 && Math.abs(v) < 5) {
                    if (Math.abs(v) == 4 || Math.abs(u) == 11) data.setBlock(x, seaLevel + 1, z, Material.IRON_BLOCK);
                    else data.setBlock(x, seaLevel + 1, z, Material.AIR);
                }
                if (u == 0 && v == 0) for (int y = 1; y <= 12 && seaLevel + y < data.getMaxHeight(); y++) {
                    data.setBlock(x, seaLevel + y, z, y % 2 == 0 ? Material.STRIPPED_SPRUCE_LOG : Material.AIR);
                }
            }
        }
    }

    public SettlementPlanner planner() { return planner; }
}