package dev.rotbound;
import dev.rotbound.weapons.WeaponService;
import dev.rotbound.world.RotboundWorldGenerator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
public final class RotboundPlugin extends JavaPlugin {
 private WeaponService weaponService;
 @Override public void onEnable() {
  saveDefaultConfig();
  int configVersion=getConfig().getInt("config-version",0);
  if(configVersion<2){
   // Migrate the earlier foundation alpha, whose stored config disabled world generation.
   // This runs once only; later manual enable/disable choices are preserved.
   getConfig().set("systems.world-generation",true);
   getConfig().set("world.target-world","world");
   getConfig().set("config-version",2);
   saveConfig();
   getLogger().info("Migrated Rotbound config to v2 and enabled Overworld generation.");
  }
  weaponService=new WeaponService(this);
  getServer().getPluginManager().registerEvents(weaponService,this);
  weaponService.startCoolingTask();
  RotboundCommand command=new RotboundCommand(this,weaponService);
  if(getCommand("rotbound")==null){getLogger().severe("Missing /rotbound command.");getServer().getPluginManager().disablePlugin(this);return;}
  getCommand("rotbound").setExecutor(command);
  getCommand("rotbound").setTabCompleter(command);
  getLogger().info("Rotbound enabled. Vanilla clients are supported.");
  if(getConfig().getBoolean("systems.world-generation",true))
   getLogger().info("Add generator: Rotbound under your main world in bukkit.yml, then explore new chunks.");
 }
 @Override public ChunkGenerator getDefaultWorldGenerator(String worldName,String id) {
  if(!getConfig().getBoolean("systems.world-generation",true))return null;
  String target=getConfig().getString("world.target-world","world");
  if(!worldName.equalsIgnoreCase(target))return null;
  return new RotboundWorldGenerator(getConfig().getInt("world.sea-level",63),getConfig().getDouble("world.structure-density",.9));
 }
 @Override public void onDisable(){if(weaponService!=null)weaponService.shutdown();}
}
