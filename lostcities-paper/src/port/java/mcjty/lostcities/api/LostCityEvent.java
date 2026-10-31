package mcjty.lostcities.api;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Event payloads retained from the upstream engine. The Paper port currently has
 * no NeoForge event bus, so these are informational payloads with uncancelled defaults.
 */
public class LostCityEvent {
    private final WorldGenLevel world;
    private final ILostCities lostCities;
    private final int chunkX;
    private final int chunkZ;

    public LostCityEvent(WorldGenLevel world, ILostCities lostCities, int chunkX, int chunkZ) {
        this.world = world;
        this.lostCities = lostCities;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public WorldGenLevel getWorld() { return world; }
    public ILostCities getLostCities() { return lostCities; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public boolean isCanceled() { return false; }

    public static final class CharacteristicsEvent extends LostCityEvent {
        private final LostChunkCharacteristics characteristics;
        public CharacteristicsEvent(WorldGenLevel world, ILostCities lostCities, int chunkX, int chunkZ,
                                    LostChunkCharacteristics characteristics) {
            super(world, lostCities, chunkX, chunkZ);
            this.characteristics = characteristics;
        }
        public LostChunkCharacteristics getCharacteristics() { return characteristics; }
    }

    public static final class PreGenCityChunkEvent extends LostCityEvent {
        private final ChunkAccess primer;
        public PreGenCityChunkEvent(WorldGenLevel world, ILostCities lostCities, int chunkX, int chunkZ, ChunkAccess primer) {
            super(world, lostCities, chunkX, chunkZ);
            this.primer = primer;
        }
        public ChunkAccess getChunkAccess() { return primer; }
    }

    public static final class PostGenCityChunkEvent extends LostCityEvent {
        private final ChunkAccess primer;
        public PostGenCityChunkEvent(WorldGenLevel world, ILostCities lostCities, int chunkX, int chunkZ, ChunkAccess primer) {
            super(world, lostCities, chunkX, chunkZ);
            this.primer = primer;
        }
        public ChunkAccess getChunkAccess() { return primer; }
    }

    public static final class PostGenOutsideChunkEvent extends LostCityEvent {
        private final ChunkAccess primer;
        public PostGenOutsideChunkEvent(WorldGenLevel world, ILostCities lostCities, int chunkX, int chunkZ, ChunkAccess primer) {
            super(world, lostCities, chunkX, chunkZ);
            this.primer = primer;
        }
        public ChunkAccess getChunkAccess() { return primer; }
    }

    public static final class PreExplosionEvent extends LostCityEvent {
        private final ChunkAccess primer;
        public PreExplosionEvent(WorldGenLevel world, ILostCities lostCities, int chunkX, int chunkZ, ChunkAccess primer) {
            super(world, lostCities, chunkX, chunkZ);
            this.primer = primer;
        }
        public ChunkAccess getChunkAccess() { return primer; }
    }
}
