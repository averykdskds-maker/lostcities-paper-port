package dev.rotbound.weapons;

import dev.rotbound.RotboundPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class WeaponService implements Listener {
    private final RotboundPlugin plugin;
    private final NamespacedKey weaponKey;
    private final NamespacedKey mortarKey;
    private final Random random = new Random();
    private final Map<UUID, ShotData> projectileData = new HashMap<>();
    private final Map<UUID, Map<WeaponType, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> minigunTasks = new HashMap<>();
    private final Map<UUID, Integer> minigunHeat = new HashMap<>();
    private final Map<UUID, BukkitTask> mortarTasks = new HashMap<>();
    private final Set<Material> supportedAmmo = EnumSet.noneOf(Material.class);
    private BukkitTask coolingTask;

    public WeaponService(RotboundPlugin plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "weapon_id");
        this.mortarKey = new NamespacedKey(plugin, "rotbound_mortar");
        for (WeaponType type : WeaponType.values()) supportedAmmo.add(type.ammoMaterial());
    }

    public ItemStack createWeapon(WeaponType type) {
        ItemStack item = new ItemStack(type.weaponMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + type.displayName());
        meta.setLore(List.of(
                ChatColor.GRAY + "Rotbound field weapon",
                ChatColor.DARK_GRAY + "Ammunition: " + type.ammoMaterial().name().toLowerCase().replace('_', ' '),
                ChatColor.DARK_GRAY + "ID: " + type.id()
        ));
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    public WeaponType identify(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
        return WeaponType.fromId(id);
    }

    public boolean isSupportedAmmo(Material material) {
        return supportedAmmo.contains(material);
    }

    public List<String> ammoList() {
        return ammoNames();
    }

    public List<String> ammoNames() {
        return supportedAmmo.stream().map(material -> material.name().toLowerCase()).sorted().toList();
    }

    public void startCoolingTask() {
        coolingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int cooling = Math.max(0, plugin.getConfig().getInt("weapons.minigun.cooling-per-10-ticks", 3));
            minigunHeat.replaceAll((uuid, heat) -> Math.max(0, heat - cooling));
            minigunHeat.entrySet().removeIf(entry -> entry.getValue() == 0 && !minigunTasks.containsKey(entry.getKey()));
        }, 10L, 10L);
    }

    @EventHandler
    public void onWeaponUse(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("systems.weapons", true)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        WeaponType type = identify(player.getInventory().getItemInMainHand());
        if (type == null) return;

        event.setCancelled(true);
        if (!event.getAction().isRightClick()) return;
        if (!player.hasPermission("rotbound.weapons.use")) {
            player.sendMessage(ChatColor.RED + "You cannot use weapons here.");
            return;
        }
        if (type == WeaponType.MINIGUN) {
            toggleMinigun(player);
            return;
        }
        fire(player, type);
    }

    @EventHandler
    public void preventWeaponMelee(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && identify(player.getInventory().getItemInMainHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        ShotData data = projectileData.remove(event.getEntity().getUniqueId());
        if (data == null) return;
        Location impact = event.getEntity().getLocation().clone();
        Player shooter = Bukkit.getPlayer(data.shooter());
        LivingEntity hit = event.getHitEntity() instanceof LivingEntity living ? living : null;

        switch (data.type()) {
            case PISTOL, SHOTGUN, RIFLE, SMG, SNIPER, MINIGUN -> { /* Native arrow damage is applied by Minecraft. */ }
            case FLAMETHROWER -> {
                if (hit != null) {
                    hit.setFireTicks(Math.max(hit.getFireTicks(), 60));
                    if (shooter != null) hit.damage(data.damage(), shooter);
                }
            }
            case GRENADE -> {
                Location point = impact;
                Bukkit.getScheduler().runTaskLater(plugin, () -> explode(point, shooter, data.damage(), 4.0), 20L);
            }
            case TEAR_GAS -> applyTearGas(impact, shooter);
            case RAILGUN -> applyRailgunImpact(impact, shooter, hit, data.damage());
            default -> { }
        }
        event.getEntity().remove();
    }

    @EventHandler
    public void onMortarExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        if (!tnt.getPersistentDataContainer().has(mortarKey, PersistentDataType.BYTE)) return;
        event.blockList().clear(); // Mortar blasts must never destroy generated buildings or terrain.
        BukkitTask task = mortarTasks.remove(tnt.getUniqueId());
        if (task != null) task.cancel();
        tnt.setSilent(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopMinigun(event.getPlayer().getUniqueId(), false);
    }

    private void fire(Player player, WeaponType type) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("weapons." + type.id() + ".cooldown-ms", type.cooldownMillis());
        Map<WeaponType, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long readyAt = playerCooldowns.getOrDefault(type, 0L);
        if (now < readyAt) return;
        if (!consumeAmmo(player, type.ammoMaterial(), type.ammoPerShot())) {
            player.sendMessage(ChatColor.RED + "Out of " + type.ammoMaterial().name().toLowerCase().replace('_', ' ') + ".");
            return;
        }
        if (cooldown > 0) playerCooldowns.put(type, now + cooldown);

        switch (type) {
            case PISTOL -> shootArrow(player, type, type.damage(), type.velocity());
            case RIFLE -> shootArrow(player, type, type.damage(), type.velocity());
            case SHOTGUN -> {
                int pellets = plugin.getConfig().getInt("weapons.shotgun.pellets", 6);
                pellets = Math.max(5, Math.min(7, pellets));
                for (int i = 0; i < pellets; i++) {
                    shootArrow(player, type, type.damage(), type.velocity(),
                            spread(player.getEyeLocation().getDirection(), 0.11));
                }
            }
            case FLAMETHROWER -> spawnFireball(player, type);
            case GRENADE -> launchGrenade(player, type);
            case SMG -> {
                for (int i = 0; i < 3; i++) {
                    final int delay = i * 5;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) shootArrow(player, type, type.damage(), type.velocity());
                    }, delay);
                }
            }
            case RAILGUN -> {
                Arrow arrow = shootArrow(player, type, type.damage(), type.velocity());
                arrow.setPierceLevel((byte) 4);
            }
            case TEAR_GAS -> spawnThrowableVisual(player, type, Material.POTION, type.velocity(),
                    spread(player.getEyeLocation().getDirection(), 0.01));
            case MORTAR -> launchMortar(player);
            case SNIPER -> shootArrow(player, type, type.damage(), type.velocity());
            case MINIGUN -> { }
        }
        player.playSound(player.getLocation(), type == WeaponType.FLAMETHROWER ? Sound.ENTITY_BLAZE_SHOOT : Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.0f);
    }

    private void spawnSnowball(Player player, WeaponType type, double damage, double velocity, Vector direction) {
        Snowball projectile = player.getWorld().spawn(player.getEyeLocation(), Snowball.class);
        projectile.setShooter(player);
        projectile.setVelocity(direction.multiply(velocity));
        projectile.setSilent(true);
        projectileData.put(projectile.getUniqueId(), new ShotData(player.getUniqueId(), type, damage));
    }

    private Arrow shootArrow(Player player, WeaponType type, double damage, double velocity) {
        return shootArrow(player, type, damage, velocity, player.getEyeLocation().getDirection());
    }

    private Arrow shootArrow(Player player, WeaponType type, double damage, double velocity, Vector direction) {
        Arrow arrow = player.getWorld().spawn(player.getEyeLocation(), Arrow.class);
        arrow.setShooter(player);
        arrow.setVelocity(direction.clone().normalize().multiply(velocity));
        arrow.setDamage(damage);
        arrow.setCritical(false);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setSilent(true);
        projectileData.put(arrow.getUniqueId(), new ShotData(player.getUniqueId(), type, damage));
        return arrow;
    }

    private void spawnThrowableVisual(Player player, WeaponType type, Material visualItem, double velocity, Vector direction) {
        Snowball projectile = player.getWorld().spawn(player.getEyeLocation(), Snowball.class);
        projectile.setShooter(player);
        projectile.setItem(new ItemStack(visualItem));
        projectile.setVelocity(direction.clone().normalize().multiply(velocity));
        projectile.setGravity(true);
        projectile.setSilent(true);
        projectileData.put(projectile.getUniqueId(), new ShotData(player.getUniqueId(), type, type.damage()));
    }

    private void spawnFireball(Player player, WeaponType type) {
        SmallFireball fireball = player.getWorld().spawn(player.getEyeLocation(), SmallFireball.class);
        fireball.setShooter(player);
        fireball.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(type.velocity()));
        fireball.setIsIncendiary(false);
        fireball.setYield(0.0f);
        fireball.setSilent(true);
        projectileData.put(fireball.getUniqueId(), new ShotData(player.getUniqueId(), type, type.damage()));
    }

    private void launchGrenade(Player player, WeaponType type) {
        // Native EnderPearl gives the grenade its actual vanilla projectile model,
        // ballistic drop and block collision. A null shooter prevents pearl teleportation.
        EnderPearl grenade = player.getWorld().spawn(player.getEyeLocation(), EnderPearl.class);
        grenade.setShooter(null);
        grenade.setVelocity(spread(player.getEyeLocation().getDirection(), 0.015).multiply(type.velocity()));
        grenade.setGravity(true);
        grenade.setSilent(true);
        projectileData.put(grenade.getUniqueId(), new ShotData(player.getUniqueId(), type, type.damage()));
    }

    private void launchMortar(Player player) {
        TNTPrimed tnt = player.getWorld().spawn(player.getEyeLocation(), TNTPrimed.class);
        Vector forward = player.getEyeLocation().getDirection().clone().setY(0).normalize();
        if (forward.lengthSquared() < 0.001) forward = new Vector(0, 0, 1);
        tnt.setVelocity(forward.multiply(2.48).add(new Vector(0, 2.48, 0)));
        // Long safety fuse; a server task shortens it as soon as the shell lands.
        tnt.setFuseTicks(plugin.getConfig().getInt("weapons.mortar.fuse-ticks", 1200));
        tnt.setYield((float) plugin.getConfig().getDouble("weapons.mortar.yield", 4.0));
        tnt.setIsIncendiary(false);
        tnt.setSilent(true);
        tnt.getPersistentDataContainer().set(mortarKey, PersistentDataType.BYTE, (byte) 1);

        UUID id = tnt.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks;
            @Override public void run() {
                if (!tnt.isValid()) {
                    BukkitTask self = mortarTasks.remove(id);
                    if (self != null) self.cancel();
                    return;
                }
                ticks++;
                if (ticks >= 6 && (tnt.isOnGround() || ticks >= 20 * 30)) {
                    tnt.setFuseTicks(1);
                    BukkitTask self = mortarTasks.remove(id);
                    if (self != null) self.cancel();
                }
            }
        }, 1L, 1L);
        mortarTasks.put(id, task);
    }

    private void toggleMinigun(Player player) {
        UUID id = player.getUniqueId();
        if (minigunTasks.containsKey(id)) {
            stopMinigun(id, true);
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Minigun firing mode enabled. Right-click again to stop.");
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || identify(player.getInventory().getItemInMainHand()) != WeaponType.MINIGUN) {
                stopMinigun(id, true);
                return;
            }
            int heat = minigunHeat.getOrDefault(id, 0);
            if (heat >= plugin.getConfig().getInt("weapons.minigun.max-heat", 100)) {
                stopMinigun(id, true);
                player.sendMessage(ChatColor.RED + "Minigun overheated.");
                return;
            }
            if (!consumeAmmo(player, Material.GUNPOWDER, 1)) {
                stopMinigun(id, true);
                player.sendMessage(ChatColor.RED + "Minigun out of gunpowder.");
                return;
            }
            int count = Math.max(1, Math.min(4, plugin.getConfig().getInt("weapons.minigun.projectiles-per-volley", 2)));
            for (int i = 0; i < count; i++) {
                shootArrow(player, WeaponType.MINIGUN, WeaponType.MINIGUN.damage(), 4.0,
                        spread(player.getEyeLocation().getDirection(), 0.08));
            }
            minigunHeat.put(id, heat + plugin.getConfig().getInt("weapons.minigun.heat-per-volley", 5));
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.18f, 1.9f);
        }, 0L, Math.max(2L, plugin.getConfig().getLong("weapons.minigun.interval-ticks", 4L)));
        minigunTasks.put(id, task);
    }

    private void stopMinigun(UUID id, boolean retainHeat) {
        BukkitTask task = minigunTasks.remove(id);
        if (task != null) task.cancel();
        if (!retainHeat) minigunHeat.remove(id);
    }

    private void applyTearGas(Location location, Player shooter) {
        double radius = plugin.getConfig().getDouble("weapons.tear_gas.radius", 6.0);
        int duration = plugin.getConfig().getInt("weapons.tear_gas.duration-ticks", 160);
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(shooter)) continue;
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 0));
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 2));
        }
        location.getWorld().spawnParticle(Particle.CLOUD, location, 35, 1.2, 0.7, 1.2, 0.02);
    }

    private void applyRailgunImpact(Location location, Player shooter, LivingEntity directHit, double damage) {
        double radius = plugin.getConfig().getDouble("weapons.railgun.splash-radius", 2.0);
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(shooter) || entity.equals(directHit)) continue;
            living.damage(damage, shooter);
        }
        location.getWorld().spawnParticle(Particle.CRIT, location, 20, 0.3, 0.3, 0.3, 0.2);
    }

    private void explode(Location location, Player shooter, double damage, double radius) {
        if (location.getWorld() == null) return;
        double configuredRadius = plugin.getConfig().getDouble("weapons.grenade.radius", radius);
        double configuredDamage = plugin.getConfig().getDouble("weapons.grenade.damage", damage);
        for (Entity entity : location.getWorld().getNearbyEntities(location, configuredRadius, configuredRadius, configuredRadius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(shooter)) continue;
            double distance = entity.getLocation().distance(location);
            if (distance <= configuredRadius) {
                double scaled = configuredDamage * (1.0 - distance / Math.max(0.1, configuredRadius));
                if (scaled > 0) living.damage(scaled, shooter);
            }
        }
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
    }

    private boolean consumeAmmo(Player player, Material material, int amount) {
        if (player.getGameMode() == GameMode.CREATIVE) return true;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int available = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material && identify(item) == null) {
                available += item.getAmount();
            }
        }
        if (available < amount) return false;

        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material || identify(item) != null) continue;
            int take = Math.min(remaining, item.getAmount());
            int left = item.getAmount() - take;
            if (left <= 0) contents[i] = null;
            else item.setAmount(left);
            remaining -= take;
        }
        inventory.setStorageContents(contents);
        return true;
    }

    private Vector spread(Vector base, double amount) {
        if (amount <= 0) return base.clone().normalize();
        return base.clone().add(new Vector(
                random.nextGaussian() * amount,
                random.nextGaussian() * amount,
                random.nextGaussian() * amount
        )).normalize();
    }

    public void shutdown() {
        if (coolingTask != null) coolingTask.cancel();
        for (BukkitTask task : minigunTasks.values()) task.cancel();
        for (BukkitTask task : mortarTasks.values()) task.cancel();
        minigunTasks.clear();
        mortarTasks.clear();
        projectileData.clear();
        cooldowns.clear();
        minigunHeat.clear();
    }

    private record ShotData(UUID shooter, WeaponType type, double damage) {
    }
}
