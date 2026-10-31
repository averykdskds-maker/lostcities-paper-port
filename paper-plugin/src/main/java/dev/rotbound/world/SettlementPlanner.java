package dev.rotbound.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Seed-stable, region-based plan for the inhabited world.
 *
 * Coordinates use one Minecraft block per metre. The map is synthetic rather than
 * a geospatial copy of Earth, but the settlement hierarchy and transport links are
 * generated consistently everywhere in the Overworld.
 */
public final class SettlementPlanner {
    public static final int REGION_BLOCKS = 8192;
    public static final int COUNTRY_BLOCKS = 1_048_576;
    public static final int PLOT_BLOCKS = 64;
    private static final int MAX_RADIUS = 2600;
    private static final String[] FIRST = {"Ar","Bel","Cor","Dun","Eld","Fen","Gal","Har","Is","Jun","Kel","Lor","Mor","Nor","Or","Pel","Quel","Rav","Sel","Tor","Val","West","Yor","Ash","Bran","Cal","Dra","Est","Farl","Grey"};
    private static final String[] LAST = {"abria","adell","and","aria","ast","avia","bourne","crest","donia","en","eshire","ford","heim","mere","ovia","ria","stan","vale","wick","holm","mark","port","reach","shire","haven","fall","wood"};
    private static final String[] STYLES = {"North Atlantic","Continental","Mediterranean","Eastern","Coastal","Highland","Industrial"};

    public List<Settlement> settlementsForChunk(long seed, int chunkX, int chunkZ) {
        int bx = chunkX * 16 + 8, bz = chunkZ * 16 + 8;
        int rx = Math.floorDiv(bx, REGION_BLOCKS), rz = Math.floorDiv(bz, REGION_BLOCKS);
        List<Settlement> out = new ArrayList<>();
        for (int z = rz - 1; z <= rz + 1; z++) {
            for (int x = rx - 1; x <= rx + 1; x++) {
                for (Settlement s : settlementsInRegion(seed, x, z)) {
                    if (Math.abs(s.x - bx) <= s.rx + 40 && Math.abs(s.z - bz) <= s.rz + 40) out.add(s);
                }
            }
        }
        return out;
    }

    public Settlement findNearestSettlement(long seed, int x, int z, boolean citiesOnly) {
        int rx = Math.floorDiv(x, REGION_BLOCKS), rz = Math.floorDiv(z, REGION_BLOCKS);
        Settlement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (Settlement s : settlementsInRegion(seed, rx + dx, rz + dz)) {
                    if (citiesOnly && !s.isUrban()) continue;
                    double ax = s.x - x, az = s.z - z, d = ax * ax + az * az;
                    if (d < bestDistance) { bestDistance = d; best = s; }
                }
            }
        }
        return best;
    }

    public Country countryAt(long seed, int x, int z) {
        int gx = Math.floorDiv(x, COUNTRY_BLOCKS), gz = Math.floorDiv(z, COUNTRY_BLOCKS);
        Random r = new Random(mix(seed ^ 0x6A09E667F3BCC909L, gx, gz));
        String name = FIRST[r.nextInt(FIRST.length)] + LAST[r.nextInt(LAST.length)];
        return new Country(name, STYLES[r.nextInt(STYLES.length)], gx, gz);
    }

    /** Returns city-to-city highway links whose bounding boxes intersect this chunk. */
    public List<RoadSegment> highwaysForChunk(long seed, int chunkX, int chunkZ) {
        int bx = chunkX * 16 + 8, bz = chunkZ * 16 + 8;
        int rx = Math.floorDiv(bx, REGION_BLOCKS), rz = Math.floorDiv(bz, REGION_BLOCKS);
        List<Settlement> hubs = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (Settlement s : settlementsInRegion(seed, rx + dx, rz + dz)) {
                    if (s.isMajorHub()) hubs.add(s);
                }
            }
        }

        int minX = chunkX * 16 - 16, maxX = chunkX * 16 + 31;
        int minZ = chunkZ * 16 - 16, maxZ = chunkZ * 16 + 31;
        List<RoadSegment> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Settlement a : hubs) {
            Settlement nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Settlement b : hubs) {
                if (a.id == b.id) continue;
                double dx = a.x - b.x, dz = a.z - b.z, d = dx * dx + dz * dz;
                if (d < nearestDistance) { nearestDistance = d; nearest = b; }
            }
            if (nearest == null || nearestDistance > 14_000.0 * 14_000.0) continue;
            String key = Math.min(a.id, nearest.id) + ":" + Math.max(a.id, nearest.id);
            if (!seen.add(key)) continue;
            int ax = a.x, az = a.z, bx2 = nearest.x, bz2 = nearest.z;
            if (Math.max(ax, bx2) < minX || Math.min(ax, bx2) > maxX
                    || Math.max(az, bz2) < minZ || Math.min(az, bz2) > maxZ) continue;
            result.add(new RoadSegment(ax, az, bx2, bz2, a.id ^ nearest.id));
        }
        return result;
    }

    private List<Settlement> settlementsInRegion(long seed, int rx, int rz) {
        Random r = new Random(mix(seed ^ 0x51ED2705L, rx, rz));
        // A hierarchy in every 8.2 km macro-region: major cities, satellite towns,
        // suburbs, village/rural areas and specialist infrastructure.
        Type[] types = {
                Type.METROPOLIS, Type.CITY, Type.CITY,
                Type.TOWN, Type.TOWN, Type.TOWN,
                Type.SUBURB, Type.SUBURB, Type.VILLAGE,
                Type.INDUSTRIAL, Type.MILITARY_BASE, Type.AIRPORT,
                Type.PORT, Type.RURAL
        };
        List<Settlement> out = new ArrayList<>(types.length);
        for (int i = 0; i < types.length; i++) {
            int col = i % 4, row = i / 4;
            int x = rx * REGION_BLOCKS + col * 2048 + 1024 + r.nextInt(641) - 320;
            int z = rz * REGION_BLOCKS + row * 2048 + 1024 + r.nextInt(641) - 320;
            Type type = types[i];
            int radius = radius(type, r);
            int rxRadius = radius + r.nextInt(Math.max(1, radius / 8));
            int rzRadius = Math.max(100, radius - r.nextInt(Math.max(1, radius / 9)));
            long id = mix(seed ^ (0x9E3779B97F4A7C15L + i), x, z);
            out.add(new Settlement(id, type, x, z, rxRadius, rzRadius, r.nextInt(4), countryAt(seed, x, z)));
        }
        return out;
    }

    private int radius(Type type, Random r) {
        return switch (type) {
            case METROPOLIS -> 1900 + r.nextInt(700);
            case CITY -> 1150 + r.nextInt(550);
            case TOWN -> 520 + r.nextInt(380);
            case SUBURB -> 420 + r.nextInt(380);
            case VILLAGE -> 180 + r.nextInt(220);
            case INDUSTRIAL -> 580 + r.nextInt(380);
            case MILITARY_BASE -> 420 + r.nextInt(330);
            case AIRPORT -> 700 + r.nextInt(350);
            case PORT -> 500 + r.nextInt(350);
            case RURAL -> 180 + r.nextInt(260);
        };
    }

    public static long mix(long seed, int x, int z) {
        long v = seed ^ ((long) x * 0x9E3779B97F4A7C15L);
        v = Long.rotateLeft(v, 27);
        v ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        v ^= v >>> 30; v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 27; v *= 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }

    public static String nameFor(long id) {
        Random r = new Random(id);
        return FIRST[r.nextInt(FIRST.length)] + LAST[r.nextInt(LAST.length)];
    }

    public enum Type {
        METROPOLIS, CITY, TOWN, SUBURB, VILLAGE, INDUSTRIAL,
        MILITARY_BASE, AIRPORT, PORT, RURAL
    }

    public record Country(String name, String architecture, int gridX, int gridZ) {}

    public record RoadSegment(int x1, int z1, int x2, int z2, long seed) {
        public double distanceSquared(double x, double z) {
            double dx = x2 - x1, dz = z2 - z1;
            double length = dx * dx + dz * dz;
            if (length == 0) return (x - x1) * (x - x1) + (z - z1) * (z - z1);
            double t = Math.max(0, Math.min(1, ((x - x1) * dx + (z - z1) * dz) / length));
            double px = x1 + t * dx, pz = z1 + t * dz;
            return (x - px) * (x - px) + (z - pz) * (z - pz);
        }
    }

    public record Settlement(long id, Type type, int x, int z, int rx, int rz, int rotation, Country country) {
        public boolean isUrban() {
            return type == Type.METROPOLIS || type == Type.CITY || type == Type.TOWN;
        }
        public boolean isMajorHub() {
            return type == Type.METROPOLIS || type == Type.CITY;
        }
        public double[] toLocal(double wx, double wz) {
            double dx = wx - x, dz = wz - z;
            return switch (rotation & 3) {
                case 1 -> new double[]{dz, -dx};
                case 2 -> new double[]{-dx, -dz};
                case 3 -> new double[]{-dz, dx};
                default -> new double[]{dx, dz};
            };
        }
        public int[] toWorld(double u, double v) {
            return switch (rotation & 3) {
                case 1 -> new int[]{x - (int) Math.round(v), z + (int) Math.round(u)};
                case 2 -> new int[]{x - (int) Math.round(u), z - (int) Math.round(v)};
                case 3 -> new int[]{x + (int) Math.round(v), z - (int) Math.round(u)};
                default -> new int[]{x + (int) Math.round(u), z + (int) Math.round(v)};
            };
        }
        public double normalizedDistance(double wx, double wz) {
            double[] p = toLocal(wx, wz);
            return Math.sqrt(p[0] * p[0] / (rx * (double) rx) + p[1] * p[1] / (rz * (double) rz));
        }
        public boolean contains(double wx, double wz) {
            double[] p = toLocal(wx, wz);
            double phase = (id & 65535) / 65535.0 * Math.PI * 2;
            double edge = 1 + .065 * Math.sin(p[0] / 171 + phase) * Math.cos(p[1] / 213 - phase)
                    + .035 * Math.sin((p[0] + p[1]) / 97 + phase * .7);
            double n = p[0] * p[0] / (rx * (double) rx) + p[1] * p[1] / (rz * (double) rz);
            return n <= edge;
        }
        public boolean isRoad(double wx, double wz) {
            double[] p = toLocal(wx, wz);
            double u = p[0] + Math.sin(p[1] / 590 + (id & 255)) * 13.0;
            double v = p[1] + Math.sin(p[0] / 610 - ((id >>> 8) & 255)) * 13.0;
            double spacing = 64.0;
            double du = Math.abs(Math.floorMod((int) Math.round(u + 32), (int) spacing) - 32);
            double dv = Math.abs(Math.floorMod((int) Math.round(v + 32), (int) spacing) - 32);
            if (du <= 4 || dv <= 4) return true;
            double majorU = Math.abs(Math.floorMod((int) Math.round(u + 192), 384) - 192);
            double majorV = Math.abs(Math.floorMod((int) Math.round(v + 192), 384) - 192);
            if (majorU <= 10 || majorV <= 10) return true;
            if (Math.abs(u) <= 13 || Math.abs(v) <= 13) return true;
            double ring = Math.sqrt(u * u / (rx * (double) rx) + v * v / (rz * (double) rz));
            return Math.abs(ring - .78) < .012;
        }
        public boolean isRunway(double wx, double wz) {
            if (type != Type.AIRPORT) return false;
            double[] p = toLocal(wx, wz);
            return Math.abs(p[1]) < 23 && Math.abs(p[0]) < rx * .88;
        }
        public String displayName() {
            return switch (type) {
                case METROPOLIS -> "Metropolitan Area";
                case CITY -> "City";
                case TOWN -> "Town";
                case SUBURB -> "Suburban District";
                case VILLAGE -> "Village";
                case INDUSTRIAL -> "Industrial Zone";
                case MILITARY_BASE -> "Military Installation";
                case AIRPORT -> "Airport";
                case PORT -> "Port";
                case RURAL -> "Rural Settlement";
            };
        }
    }
}