package mcjty.lostcities;

import mcjty.lostcities.setup.ModSetup;
import mcjty.lostcities.setup.Config;
import mcjty.lostcities.worldgen.LostCityFeature;
import mcjty.lostcities.api.LostCityEvent;
import mcjty.lostcities.worldgen.lost.cityassets.AssetRegistryBootstrap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class LostCities {
    public static final String MODID = "lostcities";
    public static final Logger LOGGER = LogManager.getLogger("LostCities");
    public static final ModSetup setup = new ModSetup();
    public static final LostCitiesImp lostCitiesImp = new LostCitiesImp();
    public static final LostCityFeature LOSTCITY_FEATURE = new LostCityFeature();

    private static volatile JavaPlugin plugin;
    private static volatile net.minecraft.server.MinecraftServer server;
    private static volatile Path configDir = Path.of("config");

    private LostCities() {}

    public static void initialize(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
        server = ((CraftServer) javaPlugin.getServer()).getServer();
        Path root = javaPlugin.getServer().getWorldContainer().toPath();
        configDir = root.resolve("config");
        Config.initialize(configDir);
        setup.preInit();
        AssetRegistryBootstrap.initialize(javaPlugin);
    }

    public static JavaPlugin getPlugin() {
        return plugin;
    }

    public static net.minecraft.server.MinecraftServer getServer() {
        return server;
    }

    public static Path getConfigDir() {
        return configDir;
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    /** The standalone Paper port has no NeoForge event bus; return the unmodified event. */
    public static <T extends LostCityEvent> T post(T event) {
        return event;
    }
}
