package dev.rotbound.lostcitiespaper;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class LostCitiesPaperPlugin extends JavaPlugin {
    private AssetPack assetPack;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            assetPack = AssetPack.load(this);
        } catch (Exception exception) {
            getLogger().severe("Could not load the Modern Tweaks asset pack: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (assetPack.assetCount() < 100) {
            getLogger().severe("The embedded Modern Tweaks datapack is missing or incomplete (loaded "
                    + assetPack.assetCount() + " JSON assets).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LostCitiesCommand command = new LostCitiesCommand(this);
        if (getCommand("lostcities") == null) {
            getLogger().severe("The /lostcities command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("lostcities").setExecutor(command);
        getCommand("lostcities").setTabCompleter(command);

        getLogger().info("Loaded " + assetPack.assetCount() + " Modern Tweaks JSON assets.");
        getLogger().info("Use /lostcities create <worldname> to create a new generated world.");
        getLogger().warning("This is an early port: city lots, streets, and basic buildings are implemented; advanced Lost Cities systems are not yet complete.");
    }

    public AssetPack assets() {
        return assetPack;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (assetPack == null) {
            return null;
        }
        return new LostCitiesChunkGenerator(this, assetPack);
    }
}
