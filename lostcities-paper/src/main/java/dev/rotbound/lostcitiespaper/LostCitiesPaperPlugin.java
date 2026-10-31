package dev.rotbound.lostcitiespaper;

import org.bukkit.Bukkit;
import org.bukkit.World;
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
        this.populator = new LostCitiesPopulator(this);
        getServer().getPluginManager().registerEvents(this, this);
        for (World world : Bukkit.getWorlds()) {
            attach(world);
        }
        getLogger().info("LostCitiesPaper enabled. Waiting for Paper FEATURES-stage integration.");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attach(event.getWorld());
    }

    private void attach(World world) {
        if (populator == null) return;
        if (world.getName().equalsIgnoreCase(getConfig().getString("target-world", "world"))
                && world.getPopulators().stream().noneMatch(p -> p instanceof LostCitiesPopulator)) {
            world.getPopulators().add(populator);
            getLogger().info("Attached Lost Cities populator to world '" + world.getName() + "'.");
        }
    }
}
