package dev.rotbound.lostcitiespaper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AssetPack {
    private final Map<String, JsonObject> json = new HashMap<>();
    private final Map<String, Part> partCache = new ConcurrentHashMap<>();
    private final Map<String, BuildingDefinition> buildingCache = new ConcurrentHashMap<>();
    private final Map<String, BlockData> blockDataCache = new ConcurrentHashMap<>();
    private final JsonObject commonCityStyle;

    private AssetPack(JsonObject commonCityStyle) {
        this.commonCityStyle = commonCityStyle;
    }

    public static AssetPack load(JavaPlugin plugin) throws Exception {
        InputStream stream = plugin.getResource("modern-tweaks.zip");
        if (stream == null) {
            Path external = plugin.getDataFolder().toPath().resolve("modern-tweaks.zip");
            if (Files.isRegularFile(external)) {
                stream = Files.newInputStream(external);
            }
        }
        if (stream == null) {
            throw new IllegalStateException("modern-tweaks.zip was not embedded in the plugin JAR");
        }

        AssetPack pack = new AssetPack(null);
        try (InputStream input = stream; ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().startsWith("data/") && entry.getName().endsWith(".json")) {
                    String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    try {
                        pack.json.put(entry.getName(), JsonParser.parseString(content).getAsJsonObject());
                    } catch (RuntimeException parseException) {
                        plugin.getLogger().warning("Skipping malformed asset " + entry.getName() + ": " + parseException.getMessage());
                    }
                }
                zip.closeEntry();
            }
        }

        JsonObject common = pack.asset("citystyles", "lcmt:citystyle_common");
        return new AssetPackWithAssets(pack, common).pack;
    }

    // Copy constructor bridge keeps the pack's parsed immutable asset map while attaching the city style.
    private static final class AssetPackWithAssets {
        private final AssetPack pack;

        private AssetPackWithAssets(AssetPack source, JsonObject common) {
            this.pack = new AssetPack(common);
            this.pack.json.putAll(source.json);
        }
    }

    public int assetCount() {
        return json.size();
    }

    private JsonObject asset(String directory, String namespacedId) {
        if (namespacedId == null || namespacedId.isBlank()) {
            return null;
        }
        String id = namespacedId.trim();
        int colon = id.indexOf(':');
        String namespace = colon >= 0 ? id.substring(0, colon) : "lcmt";
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String key = "data/" + namespace + "/lostcities/" + directory + "/" + path + ".json";
        JsonObject result = json.get(key);
        if (result == null && colon < 0) {
            result = json.get("data/lcmt/lostcities/" + directory + "/" + path + ".json");
        }
        return result;
    }

    public String chooseBuilding(Random random) {
        JsonObject selectors = commonCityStyle == null ? null : commonCityStyle.getAsJsonObject("selectors");
        JsonArray buildings = selectors == null ? null : selectors.getAsJsonArray("buildings");
        String selected = chooseWeighted(buildings, "value", "factor", random);
        return selected == null ? "lcmt:building1" : selected;
    }

    public String chooseStreet(boolean intersection, Random random) {
        if (commonCityStyle == null) {
            return "lcmt:street/street_straight_base";
        }
        JsonObject streetBlocks = commonCityStyle.getAsJsonObject("streetblocks");
        JsonObject parts = streetBlocks == null ? null : streetBlocks.getAsJsonObject("parts");
        String type = intersection ? "all" : "straight";
        JsonArray values = parts == null ? null : parts.getAsJsonArray(type);
        if (values == null || values.isEmpty()) {
            values = parts == null ? null : parts.getAsJsonArray("straight");
        }
        if (values == null || values.isEmpty()) {
            return "lcmt:street/street_straight_base";
        }
        return values.get(random.nextInt(values.size())).getAsString();
    }

    private static String chooseWeighted(JsonArray values, String valueKey, String weightKey, Random random) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double total = 0;
        for (JsonElement element : values) {
            JsonObject object = element.getAsJsonObject();
            total += object.has(weightKey) ? Math.max(0.0, object.get(weightKey).getAsDouble()) : 1.0;
        }
        if (total <= 0) {
            return values.get(random.nextInt(values.size())).getAsJsonObject().get(valueKey).getAsString();
        }
        double choice = random.nextDouble() * total;
        for (JsonElement element : values) {
            JsonObject object = element.getAsJsonObject();
            double weight = object.has(weightKey) ? Math.max(0.0, object.get(weightKey).getAsDouble()) : 1.0;
            choice -= weight;
            if (choice < 0) {
                return object.get(valueKey).getAsString();
            }
        }
        return values.get(values.size() - 1).getAsJsonObject().get(valueKey).getAsString();
    }

    public BuildingDefinition building(String id) {
        return buildingCache.computeIfAbsent(id, key -> {
            JsonObject object = asset("buildings", key);
            if (object == null || !object.has("parts")) {
                return BuildingDefinition.EMPTY;
            }
            List<String> floors = new ArrayList<>();
            List<String> tops = new ArrayList<>();
            for (JsonElement element : object.getAsJsonArray("parts")) {
                JsonObject part = element.getAsJsonObject();
                if (!part.has("part")) {
                    continue;
                }
                String reference = part.get("part").getAsString();
                if (part.has("top") && part.get("top").getAsBoolean()) {
                    tops.add(reference);
                } else {
                    floors.add(reference);
                }
            }
            return new BuildingDefinition(List.copyOf(floors), List.copyOf(tops));
        });
    }

    public Part part(String id, Palette fallback, Random random) {
        // All current Modern Tweaks building parts are 16x16 and resolve to the same world-style palette.
        // Ref-palette parts are also stable, so parsing them once is safe.
        Part cached = partCache.get(id);
        if (cached != null) {
            return cached;
        }

        JsonObject object = asset("parts", id);
        if (object == null || !object.has("slices")) {
            return null;
        }
        int xSize = object.has("xsize") ? object.get("xsize").getAsInt() : 16;
        int zSize = object.has("zsize") ? object.get("zsize").getAsInt() : 16;
        List<String> slices = new ArrayList<>();
        for (JsonElement sliceElement : object.getAsJsonArray("slices")) {
            JsonArray rows = sliceElement.getAsJsonArray();
            StringBuilder slice = new StringBuilder();
            for (JsonElement row : rows) {
                slice.append(row.getAsString());
            }
            slices.add(slice.toString());
        }

        Palette palette = fallback;
        if (object.has("refpalette")) {
            palette = palette(object.get("refpalette").getAsString(), fallback);
        } else if (object.has("palette")) {
            palette = parsePalette(object, fallback);
        }
        Part created = new Part(xSize, zSize, List.copyOf(slices), palette);
        Part previous = partCache.putIfAbsent(id, created);
        return previous == null ? created : previous;
    }

    public Palette standardPalette(Random random) {
        Palette combined = new Palette(null);
        JsonObject style = asset("styles", "lcmt:standard");
        JsonArray groups = style == null ? null : style.getAsJsonArray("randompalettes");
        if (groups != null) {
            for (JsonElement groupElement : groups) {
                JsonArray group = groupElement.getAsJsonArray();
                String selected = chooseWeighted(group, "palette", "factor", random);
                if (selected != null) {
                    combined.merge(palette(selected, combined));
                }
            }
        }
        // Safe fallback if a datapack version changes the style layout.
        if (combined.isEmpty()) {
            combined.merge(palette("lcmt:common", combined));
            combined.merge(palette("lcmt:default", combined));
        }
        return combined;
    }

    private Palette palette(String id, Palette parent) {
        JsonObject object = asset("palettes", id);
        if (object == null) {
            return parent;
        }
        return parsePalette(object, parent);
    }

    private Palette parsePalette(JsonObject object, Palette parent) {
        Palette result = new Palette(parent);
        JsonArray entries = object.getAsJsonArray("palette");
        if (entries == null) {
            return result;
        }
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("char")) {
                continue;
            }
            String symbol = entry.get("char").getAsString();
            if (symbol.isEmpty()) {
                continue;
            }
            List<WeightedBlock> blocks = new ArrayList<>();
            if (entry.has("block")) {
                blocks.add(new WeightedBlock(entry.get("block").getAsString(), 1000, null));
            } else if (entry.has("blocks")) {
                addWeightedBlocks(blocks, entry.getAsJsonArray("blocks"));
            } else if (entry.has("frompalette")) {
                blocks.add(new WeightedBlock(null, 1000, entry.get("frompalette").getAsString()));
            } else if (entry.has("variant")) {
                JsonObject variant = asset("variants", entry.get("variant").getAsString());
                if (variant != null && variant.has("blocks")) {
                    addWeightedBlocks(blocks, variant.getAsJsonArray("blocks"));
                } else if (variant != null && variant.has("palette")) {
                    String target = symbol;
                    for (JsonElement variantElement : variant.getAsJsonArray("palette")) {
                        JsonObject ve = variantElement.getAsJsonObject();
                        if (ve.has("char") && ve.get("char").getAsString().equals(target)) {
                            if (ve.has("block")) {
                                blocks.add(new WeightedBlock(ve.get("block").getAsString(), 1000, null));
                            } else if (ve.has("blocks")) {
                                addWeightedBlocks(blocks, ve.getAsJsonArray("blocks"));
                            } else if (ve.has("frompalette")) {
                                blocks.add(new WeightedBlock(null, 1000, ve.get("frompalette").getAsString()));
                            }
                            break;
                        }
                    }
                }
            }
            if (!blocks.isEmpty()) {
                result.put(symbol, List.copyOf(blocks));
            }
        }
        return result;
    }

    private static void addWeightedBlocks(List<WeightedBlock> destination, JsonArray array) {
        for (JsonElement element : array) {
            JsonObject block = element.getAsJsonObject();
            if (block.has("block")) {
                int weight = block.has("random") ? Math.max(1, block.get("random").getAsInt()) : 1;
                destination.add(new WeightedBlock(block.get("block").getAsString(), weight, null));
            }
        }
    }

    public BlockData blockData(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        BlockData cached = blockDataCache.get(identifier);
        if (cached != null) {
            return cached.clone();
        }
        try {
            BlockData created = Bukkit.createBlockData(identifier);
            BlockData previous = blockDataCache.putIfAbsent(identifier, created);
            return (previous == null ? created : previous).clone();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record BuildingDefinition(List<String> floorParts, List<String> topParts) {
        public static final BuildingDefinition EMPTY = new BuildingDefinition(List.of(), List.of());
    }

    public record Part(int xSize, int zSize, List<String> slices, Palette palette) {
    }

    private record WeightedBlock(String block, int weight, String fromPalette) {
    }

    public static final class Palette {
        private final Palette parent;
        private final Map<String, List<WeightedBlock>> entries = new HashMap<>();

        private Palette(Palette parent) {
            this.parent = parent;
        }

        private void put(String symbol, List<WeightedBlock> blocks) {
            entries.put(symbol, blocks);
        }

        private void merge(Palette other) {
            if (other != null) {
                entries.putAll(other.entries);
            }
        }

        private boolean isEmpty() {
            return entries.isEmpty() && parent == null;
        }

        public BlockData resolve(String symbol, Random random, AssetPack pack) {
            List<WeightedBlock> blocks = entries.get(symbol);
            if (blocks == null || blocks.isEmpty()) {
                return parent == null ? null : parent.resolve(symbol, random, pack);
            }
            int total = blocks.stream().mapToInt(WeightedBlock::weight).sum();
            int pick = random.nextInt(Math.max(1, total));
            for (WeightedBlock option : blocks) {
                pick -= option.weight();
                if (pick < 0) {
                    if (option.fromPalette() != null) {
                        return parent == null ? null : parent.resolve(option.fromPalette(), random, pack);
                    }
                    return pack.blockData(option.block());
                }
            }
            WeightedBlock fallback = blocks.get(blocks.size() - 1);
            return fallback.fromPalette() != null
                    ? (parent == null ? null : parent.resolve(fallback.fromPalette(), random, pack))
                    : pack.blockData(fallback.block());
        }
    }
}
