package dev.rotbound.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SettlementPlanner {
    // A region is roughly 16 km across at the project's one-block/one-metre abstraction.
    public static final int REGION_BLOCKS = 16384;
    // Country-like procedural regions are roughly 1,024 km across.
    public static final int COUNTRY_BLOCKS = 1048576;
    private static final int MAX_SETTLEMENT_RADIUS = 3200;
    private static final String[] FIRST = {"Ar","Bel","Cor","Dun","Eld","Fen","Gal","Har","Is","Jun","Kel","Lor","Mor","Nor","Or","Pel","Quel","Rav","Sel","Tor","Val","West","Yor"};
    private static final String[] LAST = {"abria","adell","and","aria","ast","avia","bourne","crest","donia","en","eshire","ford","heim","mere","ovia","ria","stan","vale","wick"};

    public List<Settlement> settlementsForChunk(long seed,int chunkX,int chunkZ) {
        int bx=chunkX*16+8,bz=chunkZ*16+8;
        int rx=Math.floorDiv(bx,REGION_BLOCKS),rz=Math.floorDiv(bz,REGION_BLOCKS);
        List<Settlement> out=new ArrayList<>();
        for(int z=rz-1;z<=rz+1;z++)for(int x=rx-1;x<=rx+1;x++)
            for(Settlement s:settlementsInRegion(seed,x,z))
                if(Math.abs(s.x-bx)<=s.rx+32&&Math.abs(s.z-bz)<=s.rz+32)out.add(s);
        return out;
    }

    public Settlement findNearestSettlement(long seed,int x,int z,boolean citiesOnly) {
        int rx=Math.floorDiv(x,REGION_BLOCKS),rz=Math.floorDiv(z,REGION_BLOCKS);
        Settlement best=null;double bestDistance=Double.MAX_VALUE;
        for(int dz=-5;dz<=5;dz++)for(int dx=-5;dx<=5;dx++)
            for(Settlement s:settlementsInRegion(seed,rx+dx,rz+dz)){
                if(citiesOnly&&s.type!=Type.METROPOLIS&&s.type!=Type.CITY&&s.type!=Type.TOWN)continue;
                double ax=s.x-x,az=s.z-z,d=ax*ax+az*az;
                if(d<bestDistance){bestDistance=d;best=s;}
            }
        return best;
    }

    public Country countryAt(long seed,int x,int z) {
        int gx=Math.floorDiv(x,COUNTRY_BLOCKS),gz=Math.floorDiv(z,COUNTRY_BLOCKS);
        Random r=new Random(mix(seed,gx*73428767,gz*912931));
        String name=FIRST[r.nextInt(FIRST.length)]+LAST[r.nextInt(LAST.length)];
        String[] styles={"North Atlantic","Continental","Mediterranean","Eastern","Coastal","Highland"};
        return new Country(name,styles[r.nextInt(styles.length)]);
    }

    private List<Settlement> settlementsInRegion(long seed,int rx,int rz) {
        Random r=new Random(mix(seed^0x51ED2705L,rx,rz));
        int count=4+r.nextInt(4); // 4–7 towns, cities and specialist sites per macro-region.
        List<Settlement> out=new ArrayList<>(count);
        for(int i=0;i<count;i++){
            int x=rx*REGION_BLOCKS+MAX_SETTLEMENT_RADIUS+r.nextInt(REGION_BLOCKS-2*MAX_SETTLEMENT_RADIUS);
            int z=rz*REGION_BLOCKS+MAX_SETTLEMENT_RADIUS+r.nextInt(REGION_BLOCKS-2*MAX_SETTLEMENT_RADIUS);
            Type type=chooseType(r.nextDouble());
            int radius=switch(type){
                case METROPOLIS->1800+r.nextInt(1400);
                case CITY->900+r.nextInt(1300);
                case TOWN->350+r.nextInt(600);
                case SUBURB->300+r.nextInt(550);
                case VILLAGE->120+r.nextInt(300);
                case INDUSTRIAL->450+r.nextInt(750);
                case MILITARY_BASE->300+r.nextInt(500);
                case AIRPORT->650+r.nextInt(800);
                case PORT->400+r.nextInt(700);
                case RURAL->100+r.nextInt(250);
            };
            int rxRadius=radius+r.nextInt(Math.max(1,radius/5));
            int rzRadius=Math.max(80,radius-r.nextInt(Math.max(1,radius/6)));
            out.add(new Settlement(mix(seed,x,z),type,x,z,rxRadius,rzRadius,r.nextInt(4),countryAt(seed,x,z)));
        }
        return out;
    }

    private Type chooseType(double v){
        if(v<.02)return Type.METROPOLIS;
        if(v<.15)return Type.CITY;
        if(v<.40)return Type.TOWN;
        if(v<.57)return Type.SUBURB;
        if(v<.75)return Type.VILLAGE;
        if(v<.84)return Type.INDUSTRIAL;
        if(v<.89)return Type.MILITARY_BASE;
        if(v<.92)return Type.AIRPORT;
        if(v<.95)return Type.PORT;
        return Type.RURAL;
    }

    public static long mix(long seed,int x,int z){
        long v=seed^((long)x*0x9E3779B97F4A7C15L);
        v=Long.rotateLeft(v,27);v^=(long)z*0xC2B2AE3D27D4EB4FL;
        v^=v>>>30;v*=0xBF58476D1CE4E5B9L;v^=v>>>27;v*=0x94D049BB133111EBL;
        return v^(v>>>31);
    }

    public record Country(String name,String architecture){}
    public enum Type{METROPOLIS,CITY,TOWN,SUBURB,VILLAGE,INDUSTRIAL,MILITARY_BASE,AIRPORT,PORT,RURAL}

    public record Settlement(long id,Type type,int x,int z,int rx,int rz,int rotation,Country country){
        private double[] local(int wx,int wz){
            double dx=wx-x,dz=wz-z;
            return switch(rotation&3){
                case 1->new double[]{dz,-dx};
                case 2->new double[]{-dx,-dz};
                case 3->new double[]{-dz,dx};
                default->new double[]{dx,dz};
            };
        }
        public boolean contains(int wx,int wz){
            double[] p=local(wx,wz);double u=p[0],v=p[1];
            double n=u*u/(rx*(double)rx)+v*v/(rz*(double)rz);
            double phase=(id&65535)/65535.0*Math.PI*2;
            double edge=1+.075*Math.sin(u/171+phase)*Math.cos(v/213-phase)+.035*Math.sin((u+v)/97+phase*.7);
            return n<=edge;
        }
        public boolean isRoad(int wx,int wz){
            double[] p=local(wx,wz);double u=p[0],v=p[1];
            if(Math.abs(u)<8||Math.abs(v)<8)return true;
            double bendV=Math.sin(u/455+(id&255))*12.0;
            int nearV=(int)Math.round((v-bendV)/112.0);
            for(int i=nearV-1;i<=nearV+1;i++){
                if(i==0||Math.floorMod((int)(mix(id,i,17)>>>1),5)==0)continue;
                double line=i*112.0+(((mix(id,i,101)&31)-15.5)*2.1);
                if(Math.abs(v-line-bendV)<3.1&&Math.abs(u)<rx*.94)return true;
            }
            double bendU=Math.sin(v/425+((id>>>8)&255))*10.0;
            int nearU=(int)Math.round((u-bendU)/112.0);
            for(int i=nearU-1;i<=nearU+1;i++){
                if(i==0||Math.floorMod((int)(mix(id,i,31)>>>1),5)==0)continue;
                double line=i*112.0+(((mix(id,i,303)&31)-15.5)*2.1);
                if(Math.abs(u-line-bendU)<3.1&&Math.abs(v)<rz*.94)return true;
            }
            double ring=Math.sqrt(u*u/(rx*(double)rx)+v*v/(rz*(double)rz));
            return Math.abs(ring-.72)<.012;
        }
        public boolean isRunway(int wx,int wz){
            if(type!=Type.AIRPORT)return false;
            double[] p=local(wx,wz);
            return Math.abs(p[1])<22&&Math.abs(p[0])<rx*.9;
        }
        public String displayName(){
            return switch(type){
                case METROPOLIS->"Metropolitan Area";
                case CITY->"City";
                case TOWN->"Town";
                case SUBURB->"Suburban District";
                case VILLAGE->"Village";
                case INDUSTRIAL->"Industrial Zone";
                case MILITARY_BASE->"Military Installation";
                case AIRPORT->"Airport";
                case PORT->"Port";
                case RURAL->"Rural Settlement";
            };
        }
    }
}
