package mcjty.lostcities.setup;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.config.LostCityProfile;
import mcjty.lostcities.config.ProfileSetup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side replacement for the upstream NeoForge ModConfigSpec.
 * Uses the same lostcities-server.toml keys and profile-selection semantics.
 */
public final class Config {
    public static final String CATEGORY_PROFILES = "profiles";
    public static final String CATEGORY_GENERAL = "general";
    public static final boolean DEBUG = false;

    public static final ConfigValue<String> SPECIAL_BED_BLOCK = new ConfigValue<>("minecraft:diamond_block");
    public static final ConfigValue<List<? extends String>> DIMENSION_PROFILES =
            new ConfigValue<>(List.of("minecraft:overworld=default"));
    public static final ConfigValue<String> SELECTED_PROFILE = new ConfigValue<>("default");
    public static final ConfigValue<String> SELECTED_CUSTOM_JSON = new ConfigValue<>("");
    public static final ConfigValue<Integer> TODO_QUEUE_SIZE = new ConfigValue<>(20);
    public static final ConfigValue<Boolean> FORCE_SAPLING_GROWTH = new ConfigValue<>(true);
    public static final ConfigValue<Integer> CACHE_CLEANUP_SECONDS = new ConfigValue<>(300);
    public static final ConfigValue<List<? extends String>> AVOID_STRUCTURES = new ConfigValue<>(List.of(
            "minecraft:mansion", "minecraft:jungle_pyramid", "minecraft:desert_pyramid",
            "minecraft:igloo", "minecraft:swamp_hut", "minecraft:pillager_outpost"));
    public static final ConfigValue<Boolean> AVOID_STRUCTURES_ADJACENT = new ConfigValue<>(false);
    public static final ConfigValue<Boolean> AVOID_VILLAGES = new ConfigValue<>(true);
    public static final ConfigValue<Boolean> AVOID_VILLAGES_ADJACENT = new ConfigValue<>(false);
    public static final ConfigValue<Boolean> AVOID_FLATTENING = new ConfigValue<>(true);
    public static final ConfigValue<Boolean> OPTIMIZED_HEIGHTMAP = new ConfigValue<>(false);
    public static final ConfigValue<Integer> HEIGHT_SAMPLE_SIZE = new ConfigValue<>(3);

    private static volatile Map<ResourceKey<Level>, String> dimensionProfileCache;
    private static volatile Set<Identifier> avoidedStructures;
    private static volatile Path configFile;

    private static final String DEFAULT_TOML = """
            # Lost Cities server configuration
            # Select a profile from config/lostcities/profiles/ or provide custom JSON below.
            [profiles]
            selectedProfile = "default"
            selectedCustomJson = ""
            dimensionsWithProfiles = ["minecraft:overworld=default"]

            [general]
            specialBedBlock = "minecraft:diamond_block"
            optimizedHeightmap = false
            heightSampleSize = 3
            todoQueueSize = 20
            forceSaplingGrowth = true
            cacheCleanupSeconds = 300
            avoidStructures = ["minecraft:mansion", "minecraft:jungle_pyramid", "minecraft:desert_pyramid", "minecraft:igloo", "minecraft:swamp_hut", "minecraft:pillager_outpost"]
            avoidStructuresAdjacent = false
            avoidVillages = true
            avoidVillagesAdjacent = false
            avoidFlattening = true
            """;

    private Config() {}

    public static synchronized void initialize(Path configDirectory) {
        try {
            Files.createDirectories(configDirectory);
            configFile = configDirectory.resolve("lostcities-server.toml");
            if (Files.notExists(configFile)) {
                Files.writeString(configFile, DEFAULT_TOML);
                LostCities.LOGGER.info("Created {}", configFile.toAbsolutePath());
            }
            Map<String, String> values = parseToml(Files.readAllLines(configFile));
            SELECTED_PROFILE.set(string(values, "profiles.selectedProfile", "default"));
            SELECTED_CUSTOM_JSON.set(string(values, "profiles.selectedCustomJson", ""));
            DIMENSION_PROFILES.set(stringList(values, "profiles.dimensionsWithProfiles",
                    List.of("minecraft:overworld=default")));
            SPECIAL_BED_BLOCK.set(string(values, "general.specialBedBlock", "minecraft:diamond_block"));
            OPTIMIZED_HEIGHTMAP.set(bool(values, "general.optimizedHeightmap", false));
            HEIGHT_SAMPLE_SIZE.set(integer(values, "general.heightSampleSize", 3));
            TODO_QUEUE_SIZE.set(integer(values, "general.todoQueueSize", 20));
            FORCE_SAPLING_GROWTH.set(bool(values, "general.forceSaplingGrowth", true));
            CACHE_CLEANUP_SECONDS.set(integer(values, "general.cacheCleanupSeconds", 300));
            AVOID_STRUCTURES.set(stringList(values, "general.avoidStructures", AVOID_STRUCTURES.get()));
            AVOID_STRUCTURES_ADJACENT.set(bool(values, "general.avoidStructuresAdjacent", false));
            AVOID_VILLAGES.set(bool(values, "general.avoidVillages", true));
            AVOID_VILLAGES_ADJACENT.set(bool(values, "general.avoidVillagesAdjacent", false));
            AVOID_FLATTENING.set(bool(values, "general.avoidFlattening", true));
            reset();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create/read config/lostcities-server.toml", e);
        }
    }

    public static synchronized void setSelectedProfile(String profileName) {
        SELECTED_PROFILE.set(profileName);
        SELECTED_CUSTOM_JSON.set("");
        if (configFile == null || Files.notExists(configFile)) {
            resetProfileCache();
            return;
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(configFile));
            boolean inProfiles = false;
            int profilesHeader = -1;
            int selectedProfileLine = -1;
            int selectedJsonLine = -1;
            String escaped = profileName.replace("\\", "\\\\").replace("\"", "\\\"");
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    inProfiles = trimmed.equals("[profiles]");
                    if (inProfiles) profilesHeader = i;
                    continue;
                }
                if (!inProfiles) continue;
                if (trimmed.startsWith("selectedProfile") && trimmed.contains("=")) selectedProfileLine = i;
                if (trimmed.startsWith("selectedCustomJson") && trimmed.contains("=")) selectedJsonLine = i;
            }

            if (selectedProfileLine >= 0) lines.set(selectedProfileLine, "selectedProfile = \"" + escaped + "\"");
            if (selectedJsonLine >= 0) lines.set(selectedJsonLine, "selectedCustomJson = \"\"");

            if (selectedProfileLine < 0 || selectedJsonLine < 0) {
                if (profilesHeader < 0) {
                    lines.add("");
                    lines.add("[profiles]");
                    profilesHeader = lines.size() - 1;
                }
                int insertAt = profilesHeader + 1;
                if (selectedProfileLine < 0) {
                    lines.add(insertAt++, "selectedProfile = \"" + escaped + "\"");
                }
                if (selectedJsonLine < 0) {
                    lines.add(insertAt, "selectedCustomJson = \"\"");
                }
            }

            Files.write(configFile, lines);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save active profile to " + configFile, e);
        }
        resetProfileCache();
    }

    public static Path getConfigFile() {
        return configFile;
    }

    private static Map<String, String> parseToml(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        String section = "";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int split = line.indexOf('=');
            if (split < 1) continue;
            String key = line.substring(0, split).trim();
            String value = stripComment(line.substring(split + 1).trim());
            values.put(section.isEmpty() ? key : section + "." + key, value);
        }
        return values;
    }

    private static String stripComment(String value) {
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' && (i == 0 || value.charAt(i - 1) != '\\')) quoted = !quoted;
            if (c == '#' && !quoted) return value.substring(0, i).trim();
        }
        return value;
    }

    private static String string(Map<String, String> map, String key, String fallback) {
        String value = map.get(key);
        if (value == null) return fallback;
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private static boolean bool(Map<String, String> map, String key, boolean fallback) {
        String value = map.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int integer(Map<String, String> map, String key, int fallback) {
        try { return Integer.parseInt(map.get(key).trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private static List<String> stringList(Map<String, String> map, String key, List<? extends String> fallback) {
        String value = map.get(key);
        if (value == null) return new ArrayList<>(fallback);
        String inner = value.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) inner = inner.substring(1, inner.length() - 1);
        List<String> result = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(inner);
        while (matcher.find()) {
            result.add(matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return result.isEmpty() && !inner.isBlank() ? new ArrayList<>(fallback) : result;
    }

    public static void reset() {
        dimensionProfileCache = null;
        avoidedStructures = null;
    }

    public static void resetProfileCache() {
        dimensionProfileCache = null;
    }

    public static void registerLostCityDimension(ServerLevel level, ResourceKey<Level> type, String profile) {
        Map<ResourceKey<Level>, String> cache = ensureDimensionProfiles(level);
        cache.putIfAbsent(type, profile);
    }

    public static String getProfileForDimension(Level level, ResourceKey<Level> type) {
        return ensureDimensionProfiles(level).get(type);
    }

    private static Map<ResourceKey<Level>, String> ensureDimensionProfiles(Level level) {
        Map<ResourceKey<Level>, String> current = dimensionProfileCache;
        if (current != null) return current;
        synchronized (Config.class) {
            current = dimensionProfileCache;
            if (current != null) return current;
            Map<ResourceKey<Level>, String> next = new ConcurrentHashMap<>();
            for (String mapping : DIMENSION_PROFILES.get()) {
                String[] split = mapping.split("=", 2);
                if (split.length != 2) {
                    LostCities.LOGGER.error("Bad format for config value: '{}'!", mapping);
                    continue;
                }
                try {
                    ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(split[0]));
                    if (ProfileSetup.STANDARD_PROFILES.containsKey(split[1])) {
                        next.put(dimension, split[1]);
                    } else {
                        LostCities.LOGGER.error("Cannot find profile '{}' for dimension {}!", split[1], split[0]);
                    }
                } catch (Exception e) {
                    LostCities.LOGGER.error("Invalid dimension profile mapping '{}'", mapping, e);
                }
            }

            String selectedProfile = SELECTED_PROFILE.get();
            String selectedJson = SELECTED_CUSTOM_JSON.get();
            if (selectedProfile != null && !selectedProfile.isBlank()) {
                if (!ProfileSetup.STANDARD_PROFILES.containsKey(selectedProfile)) {
                    LostCities.LOGGER.error("Selected Lost Cities profile '{}' does not exist.", selectedProfile);
                } else {
                    next.put(Level.OVERWORLD, selectedProfile);
                    if (selectedJson != null && !selectedJson.isBlank()) {
                        LostCityProfile customized = new LostCityProfile("customized", selectedJson);
                        ProfileSetup.STANDARD_PROFILES.computeIfAbsent("customized",
                                name -> new LostCityProfile("customized", false)).copyFrom(customized);
                        next.put(Level.OVERWORLD, "customized");
                    }
                }
            }
            String overworld = next.get(Level.OVERWORLD);
            LostCityProfile profile = overworld == null ? null : ProfileSetup.STANDARD_PROFILES.get(overworld);
            if (profile != null && profile.GENERATE_NETHER) {
                next.putIfAbsent(Level.NETHER, "cavern");
            }
            dimensionProfileCache = current = next;
        }
        return current;
    }

    public static boolean isAvoidedStructure(Identifier id) {
        cacheAvoidedStructures();
        return avoidedStructures.contains(id);
    }

    public static boolean hasAvoidedStructures() {
        cacheAvoidedStructures();
        return !avoidedStructures.isEmpty();
    }

    private static void cacheAvoidedStructures() {
        if (avoidedStructures != null) return;
        synchronized (Config.class) {
            if (avoidedStructures == null) {
                Set<Identifier> parsed = new HashSet<>();
                for (String value : AVOID_STRUCTURES.get()) {
                    try { parsed.add(Identifier.parse(value)); }
                    catch (Exception e) { LostCities.LOGGER.warn("Invalid structure id in avoidStructures: {}", value); }
                }
                avoidedStructures = parsed;
            }
        }
    }

    public static final class ConfigValue<T> {
        private volatile T value;
        private ConfigValue(T defaultValue) { value = defaultValue; }
        public T get() { return value; }
        public void set(T newValue) { value = newValue; }
    }
}
