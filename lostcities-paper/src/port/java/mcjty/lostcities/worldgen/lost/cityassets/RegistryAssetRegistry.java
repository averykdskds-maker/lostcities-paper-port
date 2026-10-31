package mcjty.lostcities.worldgen.lost.cityassets;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RegistryOps;
import mcjty.lostcities.LostCities;
import mcjty.lostcities.api.ILostCityAsset;
import mcjty.lostcities.api.ILostCityAssetRegistry;
import mcjty.lostcities.worldgen.lost.regassets.IAsset;
import mcjty.lostcities.worldgen.lost.regassets.data.DataTools;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.CommonLevelAccessor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Datapack-backed replacement for NeoForge's custom world registries.
 * Definitions are read from data/<namespace>/lostcities/<registry>/*.json,
 * which also supports Modern Tweaks' lcmt namespace.
 */
public final class RegistryAssetRegistry<T extends ILostCityAsset, R extends IAsset<R>>
        implements ILostCityAssetRegistry<T> {

    private final String folder;
    private final Codec<R> codec;
    private final Function<R, T> constructor;
    private final Map<Identifier, R> definitions = new ConcurrentHashMap<>();
    private final Map<Identifier, T> assets = new ConcurrentHashMap<>();

    public RegistryAssetRegistry(String folder, Codec<R> codec, Function<R, T> constructor) {
        this.folder = folder;
        this.codec = codec;
        this.constructor = constructor;
    }

    @SuppressWarnings("unchecked")
    public <S extends ILostCityAsset> ILostCityAssetRegistry<S> cast() {
        return (ILostCityAssetRegistry<S>) (ILostCityAssetRegistry<?>) this;
    }

    public synchronized void loadDefinitions(CommonLevelAccessor level) {
        ResourceManager manager = LostCities.getServer().getResourceManager();
        Map<Identifier, Resource> resources = manager.listResources(
                "lostcities/" + folder, id -> id.getPath().endsWith(".json"));
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        List<Identifier> names = new ArrayList<>(resources.keySet());
        names.sort(Comparator.comparing(Identifier::toString));
        for (Identifier name : names) {
            Resource resource = resources.get(name);
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                R decoded = codec.parse(ops, json).getOrThrow();
                decoded.setRegistryName(name);
                definitions.put(name, decoded);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load Lost Cities " + folder + " asset " + name, e);
            }
        }
    }

    public void loadAll(CommonLevelAccessor level) {
        if (definitions.isEmpty()) loadDefinitions(level);
        List<Identifier> names = new ArrayList<>(definitions.keySet());
        names.sort(Comparator.comparing(Identifier::toString));
        for (Identifier name : names) get(level, name);
    }

    @Override
    @Nullable
    public T get(CommonLevelAccessor level, String name) {
        return name == null ? null : get(level, DataTools.fromName(name));
    }

    @Override
    @Nullable
    public T get(CommonLevelAccessor level, Identifier name) {
        if (name == null) return null;
        T asset = assets.get(name);
        if (asset == null) {
            R definition = definitions.get(name);
            if (definition == null) {
                // A datapack may have been reloaded since the previous lookup.
                loadDefinitions(level);
                definition = definitions.get(name);
            }
            if (definition == null) return null;
            try (AssetRegistries.LevelScope ignored = AssetRegistries.withLevel(level)) {
                T created = constructor.apply(definition);
                T existing = assets.putIfAbsent(name, created);
                asset = existing == null ? created : existing;
            }
        }
        asset.init(level);
        return asset;
    }

    @Nonnull
    public T getOrThrow(CommonLevelAccessor level, String name) {
        if (name == null) throw new IllegalArgumentException("Null name for Lost Cities asset folder " + folder);
        T value = get(level, DataTools.fromName(name));
        if (value == null) throw new IllegalStateException("Missing Lost Cities asset '" + name + "' in " + folder);
        return value;
    }

    @Nullable
    public T getOrWarn(CommonLevelAccessor level, String name) {
        if (name == null || name.isBlank()) return null;
        T value = get(level, DataTools.fromName(name));
        if (value == null) LostCities.LOGGER.warn("Cannot find '{}' in Lost Cities asset folder '{}'", name, folder);
        return value;
    }

    @Override
    public Iterable<T> getIterable() {
        return List.copyOf(assets.values());
    }

    public int getNumAssets(CommonLevelAccessor level) {
        if (definitions.isEmpty()) loadDefinitions(level);
        return definitions.size();
    }

    public synchronized void reset() {
        definitions.clear();
        assets.clear();
    }
}
