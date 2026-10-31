package dev.rotbound;

import dev.rotbound.weapons.WeaponService;
import dev.rotbound.weapons.WeaponType;
import dev.rotbound.world.SettlementPlanner;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class RotboundCommand implements CommandExecutor, TabCompleter {
    private final RotboundPlugin plugin;
    private final WeaponService weapons;
    private final SettlementPlanner settlements = new SettlementPlanner();

    public RotboundCommand(RotboundPlugin plugin, WeaponService weapons) {
        this.plugin = plugin;
        this.weapons = weapons;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(ChatColor.DARK_GREEN + "Rotbound " + ChatColor.GRAY + "— The Forgotten Earth");
            sender.sendMessage(ChatColor.GRAY + "Server-side survival for Paper. No client mods required.");
            sender.sendMessage(ChatColor.GRAY + "Dense procedural cities, suburbs, towns and infrastructure are planned by world seed.");
            sender.sendMessage(ChatColor.GRAY + "Only newly generated chunks are changed.");
            return true;
        }
        if (args[0].equalsIgnoreCase("weapons")) {
            sender.sendMessage(ChatColor.GOLD + "Rotbound weapons:");
            for (WeaponType type : WeaponType.values()) {
                sender.sendMessage(ChatColor.YELLOW + type.id() + ChatColor.GRAY + " — " + type.displayName());
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("locate")) return locate(sender, args);
        if (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("visit")) return visit(sender, args);

        if (!sender.hasPermission("rotbound.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Rotbound configuration reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("weapon") && args.length >= 4 && args[1].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            WeaponType type = WeaponType.fromId(args[3]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
            if (type == null) {
                sender.sendMessage(ChatColor.RED + "Unknown weapon. Use /rotbound weapons.");
                return true;
            }
            ItemStack item = weapons.createWeapon(type);
            target.getInventory().addItem(item).values()
                    .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            sender.sendMessage(ChatColor.GREEN + "Gave " + type.displayName() + " to " + target.getName() + ".");
            return true;
        }
        if (args[0].equalsIgnoreCase("ammo") && args.length >= 4 && args[1].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            Material material = materialFromName(args[3]);
            int amount = 1;
            if (args.length >= 5) {
                try {
                    amount = Math.max(1, Math.min(576, Integer.parseInt(args[4])));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                    return true;
                }
            }
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
            if (material == null || !weapons.isSupportedAmmo(material)) {
                sender.sendMessage(ChatColor.RED + "Unsupported ammunition: " + weapons.ammoList());
                return true;
            }
            target.getInventory().addItem(new ItemStack(material, amount)).values()
                    .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " " + material.name().toLowerCase(Locale.ROOT)
                    + " to " + target.getName() + ".");
            return true;
        }
        help(sender);
        return true;
    }

    private boolean locate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Use this command in-game.");
            return true;
        }
        if (!player.hasPermission("rotbound.locate")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to locate settlements.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /rotbound locate <city|country>");
            return true;
        }
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage(ChatColor.RED + "Use this command in the Overworld.");
            return true;
        }
        if (args[1].equalsIgnoreCase("country")) {
            SettlementPlanner.Country country = settlements.countryAt(player.getWorld().getSeed(),
                    player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            player.sendMessage(ChatColor.GOLD + "Procedural country region: " + ChatColor.YELLOW + country.name());
            player.sendMessage(ChatColor.GRAY + "Architectural region: " + country.architecture());
            return true;
        }
        if (args[1].equalsIgnoreCase("city") || args[1].equalsIgnoreCase("settlement")) {
            var nearest = settlements.findNearestSettlement(player.getWorld().getSeed(),
                    player.getLocation().getBlockX(), player.getLocation().getBlockZ(), true);
            if (nearest == null) {
                player.sendMessage(ChatColor.RED + "No settlement found in search range.");
                return true;
            }
            double dx = nearest.x() - player.getLocation().getX();
            double dz = nearest.z() - player.getLocation().getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            player.sendMessage(ChatColor.GOLD + "Nearest planned " + nearest.displayName() + ": "
                    + ChatColor.YELLOW + SettlementPlanner.nameFor(nearest.id()));
            player.sendMessage(ChatColor.GRAY + "Coordinates: " + nearest.x() + ", " + nearest.z()
                    + " | Approx. distance: " + distance + " blocks.");
            player.sendMessage(ChatColor.DARK_GRAY + "Use /rotbound tp city to travel there.");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /rotbound locate <city|country>");
        return true;
    }

    private boolean visit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Use this command in-game.");
            return true;
        }
        if (!player.hasPermission("rotbound.locate")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to visit settlements.");
            return true;
        }
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage(ChatColor.RED + "Use this command in the Overworld.");
            return true;
        }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("city") || args[1].equalsIgnoreCase("settlement"))) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /rotbound tp city");
            return true;
        }
        var nearest = settlements.findNearestSettlement(player.getWorld().getSeed(),
                player.getLocation().getBlockX(), player.getLocation().getBlockZ(), true);
        if (nearest == null) {
            player.sendMessage(ChatColor.RED + "No settlement found in search range.");
            return true;
        }
        World world = player.getWorld();
        int x = nearest.x(), z = nearest.z();
        int y = Math.max(world.getMinHeight() + 2, world.getHighestBlockYAt(x, z) + 2);
        player.teleport(new Location(world, x + 0.5, y, z + 0.5));
        player.sendMessage(ChatColor.GREEN + "Travelling to " + SettlementPlanner.nameFor(nearest.id()) + ".");
        return true;
    }

    private Material materialFromName(String name) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_GREEN + "Rotbound commands");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound info" + ChatColor.GRAY + " — project status");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound weapons" + ChatColor.GRAY + " — list weapons");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound locate <city|country>" + ChatColor.GRAY + " — locate settlements");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound tp city" + ChatColor.GRAY + " — travel to the nearest city or town");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound weapon give <player> <weapon>" + ChatColor.GRAY + " — give a test weapon");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound ammo give <player> <material> [amount]" + ChatColor.GRAY + " — give ammunition");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound reload" + ChatColor.GRAY + " — reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return startsWith(List.of("help", "info", "weapons", "locate", "tp", "visit", "weapon", "ammo", "reload"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("locate")) return startsWith(List.of("city", "country"), args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("visit"))) return startsWith(List.of("city"), args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("weapon") || args[0].equalsIgnoreCase("ammo"))) return startsWith(List.of("give"), args[1]);
        if (args.length == 3 && args[1].equalsIgnoreCase("give")) return null;
        if (args.length == 4 && args[0].equalsIgnoreCase("weapon")) {
            return startsWith(Arrays.stream(WeaponType.values()).map(WeaponType::id).toList(), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("ammo")) return startsWith(weapons.ammoNames(), args[3]);
        return List.of();
    }

    private List<String> startsWith(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}