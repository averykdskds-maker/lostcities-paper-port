package dev.rotbound.world;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import java.util.List;
import java.util.Random;

public final class RotboundWorldGenerator extends ChunkGenerator {
    private final int seaLevel;
    private final double density;
    private final SettlementPlanner planner=new SettlementPlanner();

    public RotboundWorldGenerator(int seaLevel,double density) {
        this.seaLevel=seaLevel; this.density=Math.max(.1,Math.min(1,density));
    }
    @Override public boolean shouldGenerateSurface(){return true;}
    @Override public boolean shouldGenerateCaves(){return true;}
    @Override public boolean shouldGenerateDecorations(){return true;}
    @Override public boolean shouldGenerateStructures(){return false;}

    @Override
    public void generateSurface(WorldInfo info,Random random,int chunkX,int chunkZ,ChunkData data) {
        List<SettlementPlanner.Settlement> sites=planner.settlementsForChunk(info.getSeed(),chunkX,chunkZ);
        if(!sites.isEmpty())generateSettlements(info.getSeed(),chunkX,chunkZ,data,sites);
        generateShips(info.getSeed(),chunkX,chunkZ,data);
    }

    private void generateSettlements(long seed,int cx,int cz,ChunkData data,List<SettlementPlanner.Settlement> sites) {
        int[][] h=new int[16][16]; int min=9999,max=-9999; boolean ocean=false;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int y=Math.max(data.getMinHeight(),Math.min(data.getMaxHeight()-1,data.getHeight(HeightMap.WORLD_SURFACE_WG,x,z)-1));
            h[x][z]=y;min=Math.min(min,y);max=Math.max(max,y);
            if(data.getBiome(x,y,z).name().contains("OCEAN"))ocean=true;
        }
        if(ocean)return;
        for(SettlementPlanner.Settlement s:sites) {
            if(!s.contains(cx*16+8,cz*16+8))continue;
            roads(seed,cx,cz,data,s,h);
        }
        SettlementPlanner.Settlement site=sites.stream()
                .filter(s->s.contains(cx*16+8,cz*16+8))
                .min((a,b)->Double.compare(distance(a,cx*16+8,cz*16+8),distance(b,cx*16+8,cz*16+8))).orElse(null);
        if(site==null||max-min>7||site.isRoad(cx*16+8,cz*16+8)||site.isRunway(cx*16+8,cz*16+8))return;
        Random r=new Random(SettlementPlanner.mix(seed^site.id(),cx,cz));
        if(r.nextDouble()>buildingChance(site.type()))return;
        building(data,h,site,r);
        if(site.type()==SettlementPlanner.Type.MILITARY_BASE)militaryDetail(data,h,r);
    }

    private void roads(long seed,int cx,int cz,ChunkData data,SettlementPlanner.Settlement site,int[][] h) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int wx=cx*16+x,wz=cz*16+z;
            if(!site.contains(wx,wz))continue;
            boolean runway=site.isRunway(wx,wz);
            if(!runway&&!site.isRoad(wx,wz))continue;
            int n=(int)Math.floorMod(SettlementPlanner.mix(seed^site.id(),wx,wz),100);
            Material block=n<42?Material.GRAY_CONCRETE:n<63?Material.CRACKED_STONE_BRICKS:n<78?Material.ANDESITE:n<88?Material.MOSSY_COBBLESTONE:Material.COARSE_DIRT;
            if(runway&&Math.floorMod(wx+wz,13)==0)block=Material.WHITE_CONCRETE;
            data.setBlock(x,h[x][z],z,block);
        }
    }

    private void building(ChunkData data,int[][] h,SettlementPlanner.Settlement site,Random r) {
        int w=8+r.nextInt(7),d=8+r.nextInt(7);
        int sx=(16-w)/2+r.nextInt(Math.max(1,17-w-(16-w)/2));
        int sz=(16-d)/2+r.nextInt(Math.max(1,17-d-(16-d)/2));
        int sum=0;for(int[] col:h)for(int y:col)sum+=y;
        int base=sum/256;
        int height=Math.min(buildingHeight(site.type(),r),data.getMaxHeight()-base-12);
        if(height<4)return;
        Material wall=wall(site,r),floor=floor(site,r),foundation=foundation(site);
        int roofIntegrity=r.nextInt(55,90);
        for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++){
            int x=sx+dx,z=sz+dz,ground=h[x][z];
            if(ground<base)for(int y=ground+1;y<=base;y++)data.setBlock(x,y,z,foundation);
            else if(ground>base)for(int y=base+1;y<=ground;y++)data.setBlock(x,y,z,Material.AIR);
            data.setBlock(x,base,z,foundation);
            boolean edge=dx==0||dz==0||dx==w-1||dz==d-1;
            long col=SettlementPlanner.mix(site.id(),x+sx*31,z+sz*17);
            for(int y=1;y<=height;y++){
                int fi=y/4; boolean floorLevel=y==1||y%4==0,roof=y==height;
                long hash=SettlementPlanner.mix(col,y,fi);
                if(edge){
                    boolean window=y>1&&!floorLevel&&!roof&&((dx+(int)(col&3))%5==0||(dz+(int)((col>>>4)&3))%5==0);
                    boolean collapsed=(hash&31)<3||(roof&&(hash&99)>roofIntegrity);
                    if(window||collapsed)continue;
                    data.setBlock(x,base+y,z,roof?foundation:wall);
                }else if(floorLevel&&(fi==0||Math.floorMod((int)hash,17)>1)){
                    data.setBlock(x,base+y,z,floor);
                }else if(roof&&Math.floorMod((int)hash,11)<2){
                    data.setBlock(x,base+y,z,Material.AIR);
                }
            }
        }
        if(r.nextInt(100)<30){
            int x=sx+2+r.nextInt(Math.max(1,w-4)),z=sz+2+r.nextInt(Math.max(1,d-4));
            data.setBlock(x,base+2,z,Material.DIRT);
            if(r.nextBoolean()&&base+3<data.getMaxHeight()-1)data.setBlock(x,base+3,z,Material.OAK_SAPLING);
        }
    }

    private void militaryDetail(ChunkData data,int[][] h,Random r) {
        int x=r.nextInt(3,13),z=r.nextInt(3,13),y=h[x][z]+1;
        for(int i=0;i<7;i++)data.setBlock(x,y+i,z,i%2==0?Material.IRON_BLOCK:Material.GRAY_CONCRETE);
        data.setBlock(x+1,y+5,z,Material.IRON_BARS);
        data.setBlock(x-1,y+5,z,Material.IRON_BARS);
    }

    private void generateShips(long seed,int cx,int cz,ChunkData data) {
        int sea=Math.max(data.getMinHeight()+8,Math.min(data.getMaxHeight()-12,seaLevel));
        int rx=Math.floorDiv(cx*16+8,2048),rz=Math.floorDiv(cz*16+8,2048);
        for(int az=rz-1;az<=rz+1;az++)for(int ax=rx-1;ax<=rx+1;ax++){
            Random r=new Random(SettlementPlanner.mix(seed^0x5F3759DFL,ax,az));
            if(r.nextDouble()>.10)continue;
            shipSection(data,cx,cz,ax*2048+300+r.nextInt(1448),az*2048+300+r.nextInt(1448),r.nextInt(4),sea);
        }
    }

    private void shipSection(ChunkData data,int cx,int cz,int shipX,int shipZ,int rotation,int sea) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            if(!data.getBiome(x,sea,z).name().contains("OCEAN"))continue;
            int dx=cx*16+x-shipX,dz=cz*16+z-shipZ,u,v;
            switch(rotation){case 1->{u=dz;v=-dx;}case 2->{u=-dx;v=-dz;}case 3->{u=-dz;v=dx;}default->{u=dx;v=dz;}}
            if(Math.abs(u)>26||Math.abs(v)>7||Math.abs(v)>Math.max(2,7-Math.abs(u)/5))continue;
            long hash=SettlementPlanner.mix(shipX*31L+shipZ,cx*16+x,cz*16+z);
            Material hull=Math.floorMod(hash,7)==0?Material.MOSSY_COBBLESTONE:Material.GRAY_CONCRETE;
            for(int y=sea-3;y<=sea;y++)data.setBlock(x,y,z,y==sea&&Math.abs(v)<=5?Material.DARK_OAK_PLANKS:hull);
            if(Math.abs(u)<8&&Math.abs(v)<4){
                if(Math.abs(v)==3||Math.abs(u)==7)data.setBlock(x,sea+1,z,Material.IRON_BLOCK);
                else data.setBlock(x,sea+1,z,Material.AIR);
            }
            if(u==0&&v==0)for(int y=1;y<=8;y++)data.setBlock(x,sea+y,z,y%2==0?Material.STRIPPED_SPRUCE_LOG:Material.AIR);
        }
    }

    private double buildingChance(SettlementPlanner.Type t) {
        return switch(t){case METROPOLIS,CITY->.76*density;case TOWN->.68*density;case SUBURB->.55*density;case VILLAGE->.48*density;case INDUSTRIAL->.75*density;case MILITARY_BASE->.68*density;case AIRPORT->.32*density;case PORT->.44*density;case RURAL->.28*density;};
    }
    private int buildingHeight(SettlementPlanner.Type t,Random r) {
        return switch(t){case METROPOLIS->r.nextInt(24,68);case CITY->r.nextDouble()<.1?r.nextInt(48,76):r.nextInt(16,42);case TOWN->r.nextInt(8,22);case SUBURB->r.nextInt(4,10);case VILLAGE,RURAL->r.nextInt(4,8);case INDUSTRIAL->r.nextInt(8,20);case MILITARY_BASE->r.nextInt(5,16);case AIRPORT->r.nextInt(5,13);case PORT->r.nextInt(5,14);};
    }
    private Material wall(SettlementPlanner.Settlement s,Random r) {
        return switch(s.country().architecture()){case "Mediterranean"->r.nextBoolean()?Material.TERRACOTTA:Material.BRICKS;case "Eastern"->r.nextBoolean()?Material.STONE_BRICKS:Material.CRACKED_STONE_BRICKS;case "Coastal"->r.nextBoolean()?Material.LIGHT_GRAY_CONCRETE:Material.BRICKS;case "Highland"->r.nextBoolean()?Material.ANDESITE:Material.COBBLESTONE;case "Continental"->r.nextBoolean()?Material.GRAY_CONCRETE:Material.BRICKS;default->r.nextBoolean()?Material.GRAY_CONCRETE:Material.STONE_BRICKS;};
    }
    private Material floor(SettlementPlanner.Settlement s,Random r) {
        return switch(s.type()){case INDUSTRIAL,MILITARY_BASE,AIRPORT,PORT->Material.POLISHED_ANDESITE;case VILLAGE,RURAL,SUBURB->r.nextBoolean()?Material.OAK_PLANKS:Material.SPRUCE_PLANKS;default->Material.LIGHT_GRAY_CONCRETE;};
    }
    private Material foundation(SettlementPlanner.Settlement s) {
        return switch(s.type()){case INDUSTRIAL,MILITARY_BASE,AIRPORT,PORT->Material.GRAY_CONCRETE;default->Material.CRACKED_STONE_BRICKS;};
    }
    private double distance(SettlementPlanner.Settlement s,int x,int z){double dx=s.x()-x,dz=s.z()-z;return dx*dx+dz*dz;}
    public SettlementPlanner planner(){return planner;}
}
