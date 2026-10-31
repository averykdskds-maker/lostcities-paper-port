package dev.rotbound;

import dev.rotbound.weapons.WeaponService;
import dev.rotbound.weapons.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
            sender.sendMessage(ChatColor.GRAY + "A server-side survival project for Paper. No client mods required.");
            sender.sendMessage(ChatColor.GRAY + "World generation and reclamation are not enabled in this foundation build.");
            return true;
        }

        if (args[0].equalsIgnoreCase("weapons")) {
            sender.sendMessage(ChatColor.GOLD + "Rotbound weapons:");
            for (WeaponType type : WeaponType.values()) {
                sender.sendMessage(ChatColor.YELLOW + type.id() + ChatColor.GRAY + " — " + type.displayName()
                        + ChatColor.DARK_GRAY + " (" + type.weaponMaterial().name().toLowerCase(Locale.ROOT) + ")");
            }
            return true;
        }

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
            target.getInventory().addItem(item).values().forEach(leftover ->
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover));
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
                } catch (NumberFormatException exception) {
                    sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                    return true;
                }
            }
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
            if (material == null || !weapons.isSupportedAmmo(material)) {
                sender.sendMessage(ChatColor.RED + "Unsupported ammunition. Valid ammunition: " + weapons.ammoList());
                return true;
            }
            target.getInventory().addItem(new ItemStack(material, amount)).values().forEach(leftover ->
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " " + material.name().toLowerCase(Locale.ROOT) + " to " + target.getName() + ".");
            return true;
        }

        help(sender);
        return true;
    }

    private Material materialFromName(String name) {
        String normalized = name.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_GREEN + "Rotbound " + ChatColor.GRAY + "commands");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound info" + ChatColor.GRAY + " — project status");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound weapons" + ChatColor.GRAY + " — list weapons");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound weapon give <player> <weapon>" + ChatColor.GRAY + " — give a weapon");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound ammo give <player> <material> [amount]" + ChatColor.GRAY + " — give test ammunition");
        sender.sendMessage(ChatColor.YELLOW + "/rotbound reload" + ChatColor.GRAY + " — reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return startsWith(List.of("help", "info", "weapons", "weapon", "ammo", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("weapon") || args[0].equalsIgnoreCase("ammo"))) {
            return startsWith(List.of("give"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("give")) {
            return null;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("weapon")) {
            return startsWith(Arrays.stream(WeaponType.values()).map(WeaponType::id).toList(), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("ammo")) {
            return startsWith(weapons.ammoNames(), args[3]);
        }
        return List.of();
    }

    private List<String> startsWith(List<String> options, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
