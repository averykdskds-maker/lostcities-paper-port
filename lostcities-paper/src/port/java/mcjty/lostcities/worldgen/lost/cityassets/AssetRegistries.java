package mcjty.lostcities.worldgen.lost.cityassets;

import mcjty.lostcities.worldgen.lost.regassets.*;
import net.minecraft.world.level.CommonLevelAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the original Lost Cities datapack asset types. The only change from
 * upstream is that entries are resolved from the vanilla/Paper ResourceManager
 * rather than NeoForge custom registries.
 */
public final class AssetRegistries {
    public static final RegistryAssetRegistry<Variant, VariantRE> VARIANTS =
            new RegistryAssetRegistry<>("variants", VariantRE.CODEC, Variant::new);
    public static final RegistryAssetRegistry<Condition, ConditionRE> CONDITIONS =
            new RegistryAssetRegistry<>("conditions", ConditionRE.CODEC, Condition::new);
    public static final RegistryAssetRegistry<WorldStyle, WorldStyleRE> WORLDSTYLES =
            new RegistryAssetRegistry<>("worldstyles", WorldStyleRE.CODEC, WorldStyle::new);
    public static final RegistryAssetRegistry<CityStyle, CityStyleRE> CITYSTYLES =
            new RegistryAssetRegistry<>("citystyles", CityStyleRE.CODEC, CityStyle::new);
    public static final RegistryAssetRegistry<BuildingPart, BuildingPartRE> PARTS =
            new RegistryAssetRegistry<>("parts", BuildingPartRE.CODEC, BuildingPart::new);
    public static final RegistryAssetRegistry<Building, BuildingRE> BUILDINGS =
            new RegistryAssetRegistry<>("buildings", BuildingRE.CODEC, Building::new);
    public static final RegistryAssetRegistry<MultiBuilding, MultiBuildingRE> MULTI_BUILDINGS =
            new RegistryAssetRegistry<>("multibuildings", MultiBuildingRE.CODEC, MultiBuilding::new);
    public static final RegistryAssetRegistry<Style, StyleRE> STYLES =
            new RegistryAssetRegistry<>("styles", StyleRE.CODEC, Style::new);
    public static final RegistryAssetRegistry<Palette, PaletteRE> PALETTES =
            new RegistryAssetRegistry<>("palettes", PaletteRE.CODEC, Palette::new);
    public static final RegistryAssetRegistry<ScatteredBuilding, ScatteredRE> SCATTERED =
            new RegistryAssetRegistry<>("scattered", ScatteredRE.CODEC, ScatteredBuilding::new);
    public static final RegistryAssetRegistry<PredefinedCity, PredefinedCityRE> PREDEFINED_CITIES =
            new RegistryAssetRegistry<>("predefinedcities", PredefinedCityRE.CODEC, PredefinedCity::new);
    public static final RegistryAssetRegistry<PredefinedSphere, PredefinedSphereRE> PREDEFINED_SPHERES =
            new RegistryAssetRegistry<>("predefinedspheres", PredefinedSphereRE.CODEC, PredefinedSphere::new);
    public static final RegistryAssetRegistry<StuffObject, StuffSettingsRE> STUFF =
            new RegistryAssetRegistry<>("stuff", StuffSettingsRE.CODEC, StuffObject::new);

    public static final Map<String, List<StuffObject>> STUFF_BY_TAG = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<CommonLevelAccessor> CURRENT_LEVEL = new ThreadLocal<>();
    private static volatile boolean loaded;
    private static volatile boolean loadedPredefined;

    private AssetRegistries() {}

    public static CommonLevelAccessor currentLevel() {
        CommonLevelAccessor level = CURRENT_LEVEL.get();
        if (level == null) throw new IllegalStateException("Lost Cities asset construction requires an active level");
        return level;
    }

    public static LevelScope withLevel(CommonLevelAccessor level) {
        CommonLevelAccessor previous = CURRENT_LEVEL.get();
        CURRENT_LEVEL.set(level);
        return new LevelScope(previous);
    }

    public static final class LevelScope implements AutoCloseable {
        private final CommonLevelAccessor previous;
        private boolean closed;
        private LevelScope(CommonLevelAccessor previous) { this.previous = previous; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT_LEVEL.remove();
            else CURRENT_LEVEL.set(previous);
        }
    }

    public static synchronized void reset() {
        VARIANTS.reset();
        CONDITIONS.reset();
        WORLDSTYLES.reset();
        PARTS.reset();
        BUILDINGS.reset();
        CITYSTYLES.reset();
        MULTI_BUILDINGS.reset();
        STYLES.reset();
        PALETTES.reset();
        PREDEFINED_CITIES.reset();
        PREDEFINED_SPHERES.reset();
        SCATTERED.reset();
        STUFF.reset();
        STUFF_BY_TAG.clear();
        loaded = false;
        loadedPredefined = false;
    }

    public static void load(CommonLevelAccessor level) {
        if (loaded) return;
        synchronized (AssetRegistries.class) {
            if (loaded) return;
            // Decode all definitions first. Constructors can then resolve other assets
            // (for example palettes referring to variants) without registry-order bugs.
            VARIANTS.loadDefinitions(level);
            CONDITIONS.loadDefinitions(level);
            WORLDSTYLES.loadDefinitions(level);
            CITYSTYLES.loadDefinitions(level);
            PARTS.loadDefinitions(level);
            BUILDINGS.loadDefinitions(level);
            MULTI_BUILDINGS.loadDefinitions(level);
            STYLES.loadDefinitions(level);
            PALETTES.loadDefinitions(level);
            SCATTERED.loadDefinitions(level);
            PREDEFINED_CITIES.loadDefinitions(level);
            PREDEFINED_SPHERES.loadDefinitions(level);
            STUFF.loadDefinitions(level);

            try (LevelScope ignored = withLevel(level)) {
                VARIANTS.loadAll(level);
                CONDITIONS.loadAll(level);
                WORLDSTYLES.loadAll(level);
                CITYSTYLES.loadAll(level);
                PALETTES.loadAll(level);
                STYLES.loadAll(level);
                PARTS.loadAll(level);
                BUILDINGS.loadAll(level);
                MULTI_BUILDINGS.loadAll(level);
                SCATTERED.loadAll(level);
                STUFF.loadAll(level);
            }

            Map<String, List<StuffObject>> byTag = new HashMap<>();
            STUFF.getIterable().forEach(stuff -> stuff.getSettings().getTags().forEach(tag ->
                    byTag.computeIfAbsent(tag, key -> new ArrayList<>()).add(stuff)));
            byTag.forEach((tag, values) -> STUFF_BY_TAG.put(tag, List.copyOf(values)));
            loaded = true;
        }
    }

    public static void loadPredefinedStuff(CommonLevelAccessor level) {
        if (loadedPredefined) return;
        synchronized (AssetRegistries.class) {
            if (loadedPredefined) return;
            if (!loaded) load(level);
            try (LevelScope ignored = withLevel(level)) {
                PREDEFINED_CITIES.loadAll(level);
                PREDEFINED_SPHERES.loadAll(level);
            }
            loadedPredefined = true;
        }
    }
}
