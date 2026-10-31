package dev.rotbound.lostcitiespaper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LostCitiesCommand implements CommandExecutor, TabCompleter {
    private static final Pattern WORLD_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private final LostCitiesPaperPlugin plugin;

    public LostCitiesCommand(LostCitiesPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6LostCitiesPaper §7(alpha)");
            sender.sendMessage("§e/lostcities create <worldname> §7- create a new Lost Cities world");
            sender.sendMessage("§7World generation settings are in plugins/LostCitiesPaper/config.yml");
            return true;
        }

        if (!args[0].equalsIgnoreCase("create")) {
            sender.sendMessage("§cUnknown subcommand. Use /lostcities help.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /lostcities create <worldname>");
            return true;
        }

        String worldName = args[1];
        if (!WORLD_NAME.matcher(worldName).matches()) {
            sender.sendMessage("§cUse 1-32 letters, numbers, underscores, or hyphens for the world name.");
            return true;
        }
        if (Bukkit.getWorld(worldName) != null) {
            sender.sendMessage("§cThat world is already loaded.");
            return true;
        }

        // Resolve both paths to absolute form before comparing. Bukkit may return a
        // relative world container (for example "."), whose normalized Path is empty.
        // Comparing a child against that relative path incorrectly rejects every name.
        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldFolder = worldContainer.resolve(worldName).normalize();
        if (!worldFolder.startsWith(worldContainer)) {
            sender.sendMessage("§cInvalid world path.");
            return true;
        }
        if (Files.exists(worldFolder)) {
            sender.sendMessage("§cA world folder with that name already exists. Choose another name; existing worlds are never overwritten.");
            return true;
        }

        sender.sendMessage("§eCreating world §f" + worldName + "§e. Initial chunk generation may take a little longer.");
        try {
            LostCitiesChunkGenerator generator = new LostCitiesChunkGenerator(plugin, plugin.assets());
            WorldCreator creator = new WorldCreator(worldName)
                    .environment(World.Environment.NORMAL)
                    .generator(generator)
                    .generateStructures(false);

            World world = Bukkit.createWorld(creator);
            if (world == null) {
                sender.sendMessage("§cPaper did not create the world. Check the console for details.");
                return true;
            }

            Location spawn = generator.suggestedSpawn(world);
            world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
            sender.sendMessage("§aCreated Lost Cities world §f" + worldName + "§a.");
            sender.sendMessage("§7Spawn: §f" + spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ());
            sender.sendMessage("§7Only newly generated chunks use Lost Cities generation.");
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to create Lost Cities world '" + worldName + "': " + exception.getMessage());
            exception.printStackTrace();
            sender.sendMessage("§cWorld creation failed. See the server console.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "help").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
