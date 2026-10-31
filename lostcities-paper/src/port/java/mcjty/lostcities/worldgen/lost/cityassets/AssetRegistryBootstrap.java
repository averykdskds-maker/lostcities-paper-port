package mcjty.lostcities.worldgen.lost.cityassets;

import org.bukkit.plugin.java.JavaPlugin;

/** Initializes the resource-backed registry layer after Paper has loaded datapacks. */
public final class AssetRegistryBootstrap {
    private AssetRegistryBootstrap() {}
    public static void initialize(JavaPlugin plugin) {
        AssetRegistries.reset();
    }
}
