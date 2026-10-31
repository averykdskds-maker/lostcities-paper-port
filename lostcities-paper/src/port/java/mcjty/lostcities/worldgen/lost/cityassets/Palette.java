package mcjty.lostcities.worldgen.lost.cityassets;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.api.ILostCityAsset;
import mcjty.lostcities.varia.Tools;
import mcjty.lostcities.worldgen.lost.regassets.PaletteRE;
import mcjty.lostcities.worldgen.lost.regassets.data.BlockEntry;
import mcjty.lostcities.worldgen.lost.regassets.data.DataTools;
import mcjty.lostcities.worldgen.lost.regassets.data.PaletteEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Original Lost Cities palette parser with Paper's current server level replacing ServerLifecycleHooks. */
public class Palette implements ILostCityAsset {
    private final Identifier name;
    private final Map<Character, PE> palette = new HashMap<>();
    private final Map<BlockState, BlockState> damaged = new HashMap<>();

    public Palette(PaletteRE object) {
        name = object.getRegistryName();
        parsePaletteArray(object);
    }

    public Palette(String name) {
        this.name = Identifier.fromNamespaceAndPath(LostCities.MODID, name);
    }

    public void merge(Palette other) {
        palette.putAll(other.palette);
        damaged.putAll(other.damaged);
    }

    @Override public String getName() { return DataTools.toName(name); }
    @Override public Identifier getId() { return name; }
    public Map<BlockState, BlockState> getDamaged() { return damaged; }
    public Map<Character, PE> getPalette() { return palette; }

    public void parsePaletteArray(PaletteRE paletteRE) {
        for (PaletteEntry entry : paletteRE.getPaletteEntries()) {
            char c = entry.getChr().charAt(0);
            BlockState dmg = entry.getDamaged() == null ? null : Tools.stringToState(entry.getDamaged());
            Info info = new Info(entry.getMob(), entry.getLoot(), Boolean.TRUE.equals(entry.getTorch()), entry.getTag());
            if (entry.getBlock() != null) {
                BlockState state = Tools.stringToState(entry.getBlock());
                palette.put(c, new PE(state, info));
                if (dmg != null) damaged.put(state, dmg);
            } else if (entry.getVariant() != null) {
                CommonLevelAccessor level = AssetRegistries.currentLevel();
                Variant variant = AssetRegistries.VARIANTS.getOrThrow(level, entry.getVariant());
                List<Pair<Integer, BlockState>> blocks = variant.getBlocks();
                if (dmg != null) for (Pair<Integer, BlockState> pair : blocks) damaged.put(pair.getRight(), dmg);
                addMappingViaState(c, blocks, info);
            } else if (entry.getFrompalette() != null) {
                palette.put(c, new PE(entry.getFrompalette(), info));
            } else if (entry.getBlocks() != null) {
                List<Pair<Integer, BlockState>> blocks = new ArrayList<>();
                for (BlockEntry block : entry.getBlocks()) {
                    BlockState state = Tools.stringToState(block.block());
                    blocks.add(Pair.of(block.random(), state));
                    if (dmg != null) damaged.put(state, dmg);
                }
                addMappingViaState(c, blocks, info);
            } else {
                throw new IllegalStateException("Illegal palette " + name + "!");
            }
        }
    }

    private Palette addMappingViaState(char c, List<Pair<Integer, BlockState>> randomBlocks, Info info) {
        palette.put(c, new PE(randomBlocks.toArray(new Pair[0]), info));
        return this;
    }

    public record Info(String mobId, String loot, boolean isTorch, CompoundTag tag) {
        public boolean isSpecial() { return mobId != null || loot != null || isTorch || tag != null; }
    }
    public record PE(Object blocks, Info info) {}
}
