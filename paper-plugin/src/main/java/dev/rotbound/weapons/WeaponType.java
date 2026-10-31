package dev.rotbound.weapons;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Locale;

public enum WeaponType {
    PISTOL("pistol", "Pistol", Material.STICK, Material.GUNPOWDER, 1, 6.0, 4.0, 0),
    RIFLE("rifle", "Rifle", Material.BOW, Material.ARROW, 1, 10.0, 6.0, 0),
    SHOTGUN("shotgun", "Shotgun", Material.CROSSBOW, Material.ARROW, 1, 2.0, 4.0, 0),
    FLAMETHROWER("flamethrower", "Flamethrower", Material.BLAZE_ROD, Material.BLAZE_ROD, 1, 4.0, 2.5, 0),
    GRENADE("grenade", "Grenade", Material.ENDER_PEARL, Material.ENDER_PEARL, 1, 8.0, 1.8, 0),
    SMG("smg", "SMG", Material.IRON_SWORD, Material.ARROW, 3, 3.0, 5.0, 0),
    RAILGUN("railgun", "Railgun", Material.DIAMOND_PICKAXE, Material.DIAMOND, 1, 15.0, 12.0, 0),
    TEAR_GAS("tear_gas", "Tear Gas", Material.POTION, Material.POTION, 1, 0.0, 1.8, 0),
    MORTAR("mortar", "Mortar", Material.TNT, Material.TNT, 1, 0.0, 3.5, 0),
    SNIPER("sniper", "Sniper", Material.NETHER_STAR, Material.NETHER_STAR, 1, 25.0, 15.0, 10_000),
    MINIGUN("minigun", "Minigun", Material.IRON_PICKAXE, Material.GUNPOWDER, 1, 2.0, 4.0, 0);

    private final String id;
    private final String displayName;
    private final Material weaponMaterial;
    private final Material ammoMaterial;
    private final int ammoPerShot;
    private final double damage;
    private final double velocity;
    private final long cooldownMillis;

    WeaponType(String id, String displayName, Material weaponMaterial, Material ammoMaterial,
               int ammoPerShot, double damage, double velocity, long cooldownMillis) {
        this.id = id;
        this.displayName = displayName;
        this.weaponMaterial = weaponMaterial;
        this.ammoMaterial = ammoMaterial;
        this.ammoPerShot = ammoPerShot;
        this.damage = damage;
        this.velocity = velocity;
        this.cooldownMillis = cooldownMillis;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material weaponMaterial() { return weaponMaterial; }
    public Material ammoMaterial() { return ammoMaterial; }
    public int ammoPerShot() { return ammoPerShot; }
    public double damage() { return damage; }
    public double velocity() { return velocity; }
    public long cooldownMillis() { return cooldownMillis; }

    public static WeaponType fromId(String id) {
        if (id == null) return null;
        String normalized = id.toLowerCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values()).filter(type -> type.id.equals(normalized)).findFirst().orElse(null);
    }
}
