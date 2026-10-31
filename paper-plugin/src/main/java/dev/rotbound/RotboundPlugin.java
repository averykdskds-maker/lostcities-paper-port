package dev.rotbound;

import dev.rotbound.weapons.WeaponService;
import org.bukkit.plugin.java.JavaPlugin;

public final class RotboundPlugin extends JavaPlugin {
    private WeaponService weaponService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        weaponService = new WeaponService(this);
        getServer().getPluginManager().registerEvents(weaponService, this);
        weaponService.startCoolingTask();

        RotboundCommand command = new RotboundCommand(this, weaponService);
        if (getCommand("rotbound") == null) {
            getLogger().severe("The /rotbound command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("rotbound").setExecutor(command);
        getCommand("rotbound").setTabCompleter(command);

        getLogger().info("Rotbound is enabled.");
        getLogger().info("Weapons: " + (getConfig().getBoolean("systems.weapons", true) ? "enabled" : "disabled"));
        getLogger().info("World generation: not yet enabled; the Forgotten Earth generator is under development.");
        getLogger().info("This build is a project foundation, not the finished Rotbound world.");
    }

    public WeaponService weapons() {
        return weaponService;
    }

    @Override
    public void onDisable() {
        if (weaponService != null) {
            weaponService.shutdown();
        }
    }
}
