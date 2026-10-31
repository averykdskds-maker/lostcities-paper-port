package dev.rotbound.lostcitiespaper;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.setup.Config;
import mcjty.lostcities.config.ProfileSetup;
import mcjty.lostcities.worldgen.LostCityFeature;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
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
        String selected = getConfig().getString("profile", "");
        if (selected != null && !selected.isBlank()) {
            Config.SELECTED_PROFILE.set(selected);
            Config.resetProfileCache();
            LostCities.LOSTCITY_FEATURE.cleanUp();
        }

        this.populator = new LostCitiesPopulator(this);
        getServer().getPluginManager().registerEvents(this, this);
        for (World world : Bukkit.getWorlds()) attach(world);

        getLogger().info("Lost Cities Paper port enabled with profile '" + Config.SELECTED_PROFILE.get() + "'.");
        getLogger().info("Embedded Lost Cities + Modern Tweaks datapack must be enabled; check /datapack list enabled.");
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("LostCitiesPaper: " + Config.SELECTED_PROFILE.get());
            sender.sendMessage("Target world: " + getConfig().getString("target-world", "world"));
            sender.sendMessage("Profiles: plugins/config/lostcities/profiles");
            sender.sendMessage("Server settings: config/lostcities-server.toml");
            return true;
        }
        if (!sender.hasPermission("lostcities.admin")) {
            sender.sendMessage("You do not have permission to administer Lost Cities.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            try {
                getConfig().load(getDataFolder().toPath().resolve("config.yml").toFile());
                Config.initialize(LostCities.getConfigDir());
                ProfileSetup.setupProfiles();
                LostCities.LOSTCITY_FEATURE.cleanUp();
                sender.sendMessage("Reloaded Lost Cities profiles/config. Newly generated chunks will use the new settings.");
            } catch (Exception e) {
                sender.sendMessage("Lost Cities reload failed: " + e.getMessage());
                getLogger().severe("Could not reload Lost Cities configuration: " + e.getMessage());
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("profile") && args.length >= 2) {
            if (!ProfileSetup.STANDARD_PROFILES.containsKey(args[1])) {
                sender.sendMessage("Unknown Lost Cities profile '" + args[1] + "'.");
                return true;
            }
            Config.SELECTED_PROFILE.set(args[1]);
            Config.SELECTED_CUSTOM_JSON.set("");
            Config.resetProfileCache();
            LostCities.LOSTCITY_FEATURE.cleanUp();
            sender.sendMessage("Active Lost Cities profile set to " + args[1] + ".");
            return true;
        }
        sender.sendMessage("Usage: /lostcities [status|reload|profile <name>]");
        return true;
    }
}
