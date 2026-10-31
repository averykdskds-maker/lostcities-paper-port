package dev.rotbound;

import dev.rotbound.weapons.WeaponService;
import dev.rotbound.world.RotboundWorldGenerator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class RotboundPlugin extends JavaPlugin {
    private WeaponService weaponService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        int configVersion = getConfig().getInt("config-version", 0);

        if (configVersion < 2) {
            // Earlier foundation builds shipped with generation disabled in existing configs.
            getConfig().set("systems.world-generation", true);
            getConfig().set("world.target-world", "world");
            getConfig().set("config-version", 2);
            saveConfig();
            getLogger().info("Migrated Rotbound configuration to v2.");
        }
        if (configVersion < 3) {
            getConfig().set("world.structure-density", 1.0);
            getConfig().set("settlements.region-size-blocks", 8192);
            getConfig().set("config-version", 3);
            saveConfig();
            getLogger().info("Migrated Rotbound configuration to v3 (expanded settlement planning).");
        }

        weaponService = new WeaponService(this);
        getServer().getPluginManager().registerEvents(weaponService, this);
        weaponService.startCoolingTask();

        RotboundCommand command = new RotboundCommand(this, weaponService);
        if (getCommand("rotbound") == null) {
            getLogger().severe("Missing /rotbound command.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("rotbound").setExecutor(command);
        getCommand("rotbound").setTabCompleter(command);
        getLogger().info("Rotbound enabled. Vanilla clients are supported.");

        if (getConfig().getBoolean("systems.world-generation", true)) {
            getLogger().info("World plan active for '" + getConfig().getString("world.target-world", "world")
                    + "'. Ensure bukkit.yml assigns generator: Rotbound to that world.");
            getLogger().info("Only newly generated chunks receive Rotbound cities, roads and infrastructure.");
        }
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (!getConfig().getBoolean("systems.world-generation", true)) return null;
        String target = getConfig().getString("world.target-world", "world");
        if (!worldName.equalsIgnoreCase(target)) return null;
        return new RotboundWorldGenerator(
                getConfig().getInt("world.sea-level", 63),
                getConfig().getDouble("world.structure-density", 1.0));
    }

    @Override
    public void onDisable() {
        if (weaponService != null) weaponService.shutdown();
    }
}