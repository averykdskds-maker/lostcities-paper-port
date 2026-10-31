package dev.rotbound.lostcitiespaper;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.config.ProfileSetup;
import mcjty.lostcities.setup.Config;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public final class LostCitiesPaperPlugin extends JavaPlugin implements Listener {
    private LostCitiesPopulator populator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().warning("Lost Cities generation is disabled in config.yml.");
            return;
        }

        LostCities.initialize(this);
        registerAdminPermission();
        registerManagementCommand();

        this.populator = new LostCitiesPopulator(this);
        getServer().getPluginManager().registerEvents(this, this);
        for (World world : Bukkit.getWorlds()) attach(world);

        String target = getConfig().getString("target-world", "world");
        getLogger().info("Lost Cities Paper port enabled. Target world: '" + target
                + "', active profile: '" + Config.SELECTED_PROFILE.get() + "'.");
        getLogger().info("Profile JSON files: " + LostCities.getConfigDir().resolve("lostcities/profiles"));
        getLogger().info("Server settings: " + Config.getConfigFile());
        getLogger().info("Embedded Lost Cities + Modern Tweaks pack is registered by the Paper bootstrapper.");
    }

    private void registerAdminPermission() {
        if (getServer().getPluginManager().getPermission("lostcities.admin") == null) {
            getServer().getPluginManager().addPermission(
                    new Permission("lostcities.admin", "Allows managing Lost Cities profiles and configuration.",
                            PermissionDefault.OP));
        }
    }

    private void registerManagementCommand() {
        registerCommand("lostcities", "Lost Cities status, profile and config management", (source, args) -> {
            CommandSender sender = source.getSender();
            if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
                sender.sendMessage("LostCitiesPaper active profile: " + Config.SELECTED_PROFILE.get());
                sender.sendMessage("Target world: " + getConfig().getString("target-world", "world"));
                sender.sendMessage("Profiles: " + LostCities.getConfigDir().resolve("lostcities/profiles"));
                sender.sendMessage("Server settings: " + Config.getConfigFile());
                return;
            }

            if (!sender.hasPermission("lostcities.admin")) {
                sender.sendMessage("You do not have permission to administer Lost Cities.");
                return;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                try {
                    reloadConfig();
                    Config.initialize(LostCities.getConfigDir());
                    ProfileSetup.setupProfiles();
                    LostCities.LOSTCITY_FEATURE.cleanUp();
                    sender.sendMessage("Reloaded Lost Cities config and profiles. Newly generated chunks will use the new settings.");
                } catch (Exception e) {
                    sender.sendMessage("Lost Cities reload failed: " + e.getMessage());
                    getLogger().severe("Could not reload Lost Cities configuration: " + e.getMessage());
                }
                return;
            }

            if (args[0].equalsIgnoreCase("profile") && args.length >= 2) {
                String profileName = args[1];
                if (!ProfileSetup.STANDARD_PROFILES.containsKey(profileName)) {
                    sender.sendMessage("Unknown Lost Cities profile '" + profileName + "'.");
                    sender.sendMessage("Create or edit a custom JSON profile in config/lostcities/profiles/ first.");
                    return;
                }
                try {
                    Config.setSelectedProfile(profileName);
                    LostCities.LOSTCITY_FEATURE.cleanUp();
                    sender.sendMessage("Active Lost Cities profile set to " + profileName + " and saved to lostcities-server.toml.");
                } catch (Exception e) {
                    sender.sendMessage("Could not save profile selection: " + e.getMessage());
                    getLogger().severe("Could not save active profile: " + e.getMessage());
                }
                return;
            }

            sender.sendMessage("Usage: /lostcities [status|reload|profile <name>]");
        });
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attach(event.getWorld());
    }

    private void attach(World world) {
        if (populator == null) return;
        String target = getConfig().getString("target-world", "world");
        if (world.getName().equalsIgnoreCase(target)
                && world.getPopulators().stream().noneMatch(p -> p instanceof LostCitiesPopulator)) {
            world.getPopulators().add(populator);
            getLogger().info("Attached original Lost Cities generation to world '" + world.getName() + "'.");
        }
    }
}
