package zooller.regnumworld.dungeon;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import zooller.regnumworld.dungeon.DungeonData.Dungeon;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.world.level.block.state.BlockState;

/** Deterministic modular networks: no exterior box shell; rooms are joined by two loops and cross-links. */
final class DungeonStructures {
    static int theme(Dungeon d){return d.type.equals("mine")?1:d.type.equals("catacombs")?2:3;}
    static int floor(Dungeon d,int room){return d.y+(d.layoutVersion>=2&&theme(d)==3&&(room==2||room==5)?3:0);}
    static BlockPos room(Dungeon d,int room){return new BlockPos(d.x+8+room%3*16,floor(d,room),d.z+8+room/3*16);}
    static void build(ServerLevel world,Dungeon d) {
        blueprint(d).forEach((pos,state)->world.setBlock(pos,state,2));
    }
    static Map<BlockPos,BlockState> blueprint(Dungeon d) {
        Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();BiConsumer<BlockPos,BlockState> sink=blocks::put;
        if(theme(d)==3)bastion(sink,d);else underground(sink,d);
        if(d.layoutVersion>=3)details(sink,d);
        return blocks;
    }
    private static void put(BiConsumer<BlockPos,BlockState> w,Dungeon d,int x,int y,int z,Block block){if(x>=0&&x<48&&z>=0&&z<48)w.accept(new BlockPos(d.x+x,y,d.z+z),block instanceof LanternBlock?block.defaultBlockState().setValue(LanternBlock.HANGING,true):block.defaultBlockState());}
    private static void fill(BiConsumer<BlockPos,BlockState> w,Dungeon d,int x1,int y1,int z1,int x2,int y2,int z2,Block block){for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)put(w,d,x,y,z,block);}
    private static void tunnel(BiConsumer<BlockPos,BlockState> w,Dungeon d,int x1,int z1,int x2,int z2,int width) {
        int length=Math.max(Math.abs(x2-x1),Math.abs(z2-z1));
        for(int step=0;step<=length;step++){int x=x1+(x2-x1)*step/Math.max(1,length),z=z1+(z2-z1)*step/Math.max(1,length);
            for(int dx=-width;dx<=width;dx++)for(int dz=-width;dz<=width;dz++) {
                put(w,d,x+dx,d.y-1,z+dz,theme(d)==1?Blocks.OAK_PLANKS:Blocks.DEEPSLATE_TILES);
                fill(w,d,x+dx,d.y,z+dz,x+dx,d.y+3,z+dz,Blocks.AIR);
                put(w,d,x+dx,d.y+4,z+dz,theme(d)==1?Blocks.STONE:Blocks.DEEPSLATE_BRICKS);
            }
        }
    }
    private static void underground(BiConsumer<BlockPos,BlockState> w,Dungeon d) {
        // Rooms vary in shape: rounded excavations for the mine, vaulted crypts and a hidden reliquary.
        Random random=new Random(d.id.getLeastSignificantBits());
        for(int room=0;room<9;room++) {
            int cx=8+room%3*16,cz=8+room/3*16,r=room==8?7:5+random.nextInt(2);
            for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++) {
                boolean inside=theme(d)==1?dx*dx+dz*dz<=r*r+3:Math.abs(dx)<r&&Math.abs(dz)<r;
                if(!inside)continue;
                if(theme(d)==2&&(Math.abs(dx)==r-1||Math.abs(dz)==r-1)) {
                    for(int h=0;h<6;h++)put(w,d,cx+dx,d.y+h,cz+dz,Blocks.DEEPSLATE_BRICKS);
                    continue;
                }
                put(w,d,cx+dx,d.y-1,cz+dz,theme(d)==1?Blocks.COARSE_DIRT:Blocks.DEEPSLATE_TILES);
                for(int h=0;h<7;h++)put(w,d,cx+dx,d.y+h,cz+dz,h==6?theme(d)==1?Blocks.STONE:Blocks.DEEPSLATE_BRICKS:Blocks.AIR);
            }
            for(int side:new int[]{-4,4}) {
                for(int h=0;h<5;h++)put(w,d,cx+side,d.y+h,cz-3,theme(d)==1?Blocks.OAK_LOG:Blocks.POLISHED_BASALT);
                if(theme(d)==2){for(int q=-2;q<=2;q++)put(w,d,cx+side,d.y,cz+q,Blocks.STONE_BRICK_SLAB);put(w,d,cx+side,d.y+1,cz,Blocks.SKELETON_SKULL);}
                else {put(w,d,cx+side,d.y,cz+3,Blocks.IRON_ORE);put(w,d,cx+side,d.y+1,cz+3,Blocks.RAW_IRON_BLOCK);}
            }
            if(theme(d)==1)for(int dx=-4;dx<=4;dx++)put(w,d,cx+dx,d.y+4,cz-3,Blocks.OAK_LOG);
            put(w,d,cx,d.y+5,cz,Blocks.LANTERN);
        }
        // Eight perimeter connections plus a central cross provide multiple approaches to every arena.
        for(int row=0;row<3;row++)for(int col=0;col<2;col++)tunnel(w,d,8+col*16,8+row*16,24+col*16,8+row*16,2);
        for(int col=0;col<3;col++)for(int row=0;row<2;row++)tunnel(w,d,8+col*16,8+row*16,8+col*16,24+row*16,2);
        if(theme(d)==1) {
            // Side-cut ravine with a broad wooden bridge and low railings, plus a visible collapsed dead end.
            fill(w,d,13,d.y-4,21,19,d.y-1,27,Blocks.AIR);
            fill(w,d,13,d.y-1,22,19,d.y-1,26,Blocks.OAK_PLANKS);
            for(int x=13;x<=19;x++){put(w,d,x,d.y,21,Blocks.OAK_FENCE);put(w,d,x,d.y,27,Blocks.OAK_FENCE);}
            tunnel(w,d,8,24,3,31,1);fill(w,d,2,d.y,30,4,d.y+2,32,Blocks.GRAVEL);
        } else {
            // Hidden, accessible side chamber off the central crypt, with a second approach.
            tunnel(w,d,24,24,31,31,1);tunnel(w,d,31,31,40,24,1);
            fill(w,d,28,d.y-1,28,34,d.y-1,34,Blocks.CHISELED_STONE_BRICKS);fill(w,d,29,d.y,29,33,d.y+3,33,Blocks.AIR);
            put(w,d,31,d.y+3,31,Blocks.SOUL_LANTERN);
        }
        entrance(w,d,true);entrance(w,d,false);
    }
    private static void entrance(BiConsumer<BlockPos,BlockState> w,Dungeon d,boolean front) {
        int surface=front?d.frontY:d.backY;
        int depth=Math.max(0,surface-d.y),run=Math.max(12,Math.min(20,depth+2));
        // Wide stairwell cuts through the surface into the network, with a parallel lower crossing.
        for(int step=0;step<=run;step++) {
            int z=front?step:47-step;
            int floor=d.y+(int)Math.round(depth*(1-step/(double)run));
            for(int dx=-2;dx<=2;dx++) {
                put(w,d,24+dx,floor-1,z,theme(d)==1?Blocks.OAK_PLANKS:Blocks.STONE_BRICKS);
                fill(w,d,24+dx,floor,z,24+dx,floor+4,z,Blocks.AIR);
            }
            if(step%4==0)for(int dx:new int[]{-3,3})fill(w,d,24+dx,floor-1,z,24+dx,floor+4,z,theme(d)==1?Blocks.OAK_LOG:Blocks.DEEPSLATE_BRICKS);
        }
        // When a forced mountain site exceeds the stair run, a side ladder covers every vertical gap.
        if(depth>run){int from=front?0:39,to=front?8:47;fill(w,d,22,d.y,from,26,d.y+3,to,Blocks.AIR);fill(w,d,22,d.y-1,from,26,d.y-1,to,Blocks.STONE_BRICKS);}
        if(depth>run)for(int y=d.y;y<=surface+1;y++) {
            int z=front?0:47;put(w,d,21,y,z,Blocks.STONE_BRICKS);
            w.accept(new BlockPos(d.x+22,y,d.z+z),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.EAST));
        }
        int z=front?1:46;fill(w,d,21,surface+4,z,27,surface+4,z,theme(d)==1?Blocks.OAK_LOG:Blocks.CHISELED_STONE_BRICKS);
        if(theme(d)==1){put(w,d,20,surface,z,Blocks.GRAVEL);put(w,d,28,surface,z,Blocks.MOSSY_COBBLESTONE);}
    }
    private static void details(BiConsumer<BlockPos,BlockState> w,Dungeon d) {
        if(theme(d)<3) {
            // Repeated structural modules make corridors readable, with cover outside the walking lanes.
            for(int row=0;row<3;row++)for(int x:new int[]{15,31}) {
                int z=8+row*16;
                for(int side:new int[]{-3,3})fill(w,d,x,d.y,z+side,x,d.y+3,z+side,theme(d)==1?Blocks.OAK_LOG:Blocks.CHISELED_STONE_BRICKS);
                for(int side=-3;side<=3;side++)put(w,d,x,d.y+4,z+side,theme(d)==1?Blocks.OAK_LOG:Blocks.STONE_BRICKS);
                put(w,d,x,d.y+3,z,theme(d)==1?Blocks.LANTERN:Blocks.SOUL_LANTERN);
            }
            for(int col=0;col<3;col++)for(int z:new int[]{15,31}) {
                int x=8+col*16;
                for(int side:new int[]{-3,3})fill(w,d,x+side,d.y,z,x+side,d.y+3,z,theme(d)==1?Blocks.OAK_LOG:Blocks.CHISELED_STONE_BRICKS);
                fill(w,d,x-3,d.y+4,z,x+3,d.y+4,z,theme(d)==1?Blocks.OAK_LOG:Blocks.STONE_BRICKS);
            }
            if(theme(d)==1) {
                // A resource face and an abandoned haulage siding off the western branch.
                tunnel(w,d,8,8,3,15,1);
                for(int z=12;z<=17;z++) {
                    put(w,d,1,d.y,z,z%2==0?Blocks.DEEPSLATE_IRON_ORE:Blocks.COAL_ORE);
                    w.accept(new BlockPos(d.x+3,d.y,d.z+z),Blocks.RAIL.defaultBlockState());
                }
                for(int x:new int[]{4,12})for(int z:new int[]{4,36})put(w,d,x,d.y,z,Blocks.OAK_PLANKS);
            } else {
                // Sunken reliquary: two broad steps lead into a lower crypt rather than a flat box.
                fill(w,d,29,d.y-3,29,33,d.y-3,33,Blocks.CHISELED_STONE_BRICKS);
                fill(w,d,29,d.y-2,29,33,d.y+2,33,Blocks.AIR);
                fill(w,d,28,d.y-1,29,28,d.y-1,33,Blocks.STONE_BRICKS);
                fill(w,d,29,d.y-2,29,29,d.y-2,33,Blocks.STONE_BRICKS);
                fill(w,d,33,d.y-2,29,33,d.y-2,33,Blocks.STONE_BRICKS);
                fill(w,d,34,d.y-1,29,34,d.y-1,33,Blocks.STONE_BRICKS);
                put(w,d,31,d.y-2,31,Blocks.CHISELED_DEEPSLATE);
                for(int x:new int[]{3,13,35,45})for(int z:new int[]{4,36}) {
                    put(w,d,x,d.y,z,Blocks.STONE_BRICK_SLAB);
                    put(w,d,x,d.y+1,z,Blocks.SKELETON_SKULL);
                }
                // Boss room niches offer cover against ranged pursuers without blocking the center.
                for(int x:new int[]{35,45})fill(w,d,x,d.y,36,x,d.y+3,37,Blocks.POLISHED_BASALT);
            }
        } else {
            // Two wall walks connect all four towers. Side ladders create alternate PvP approaches.
            for(int z:new int[]{5,42}) {
                fill(w,d,5,d.y+5,z-1,42,d.y+5,z+1,Blocks.STONE_BRICKS);
                for(int x=5;x<=42;x++)if(x%4!=0)put(w,d,x,d.y+6,z==5?z-2:z+2,Blocks.STONE_BRICK_WALL);
            }
            for(int x:new int[]{5,42})for(int z:new int[]{8,39}) {
                fill(w,d,x-1,d.y,z,x-1,d.y+6,z,Blocks.STONE_BRICKS);
                for(int y=d.y;y<=d.y+6;y++)w.accept(new BlockPos(d.x+x,y,d.z+z),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.EAST));
                fill(w,d,x,d.y+5,z-2,x+1,d.y+5,z+2,Blocks.STONE_BRICKS);
                w.accept(new BlockPos(d.x+x,d.y+5,d.z+z),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.EAST));
            }
            // Ruined gallery with windows, high roof and doors at both ends; central courtyard has low cover.
            for(int z=11;z<=22;z++)for(int x:new int[]{34,45})for(int h=3;h<=6;h++)
                if(h==3||h==6||z%4==0)put(w,d,x,d.y+h,z,Blocks.CRACKED_STONE_BRICKS);
            fill(w,d,34,d.y+7,11,45,d.y+7,22,Blocks.STONE_BRICK_SLAB);
            for(int x:new int[]{17,30})for(int z:new int[]{19,29}) {
                fill(w,d,x,d.y,z,x+1,d.y+1,z+1,Blocks.MOSSY_COBBLESTONE);
                put(w,d,x,d.y+2,z,Blocks.COBBLESTONE_WALL);
            }
        }
    }
    private static void bastion(BiConsumer<BlockPos,BlockState> w,Dungeon d) {
        // Open courtyards with broken perimeter walls, four towers and elevated east-side halls.
        fill(w,d,2,d.y-1,2,45,d.y-1,45,Blocks.STONE_BRICKS);
        for(int x=2;x<=45;x++)for(int z:new int[]{2,45})if(Math.abs(x-24)>3)for(int h=0;h<4+(x%5==0?1:0);h++)put(w,d,x,d.y+h,z,Blocks.MOSSY_STONE_BRICKS);
        for(int z=2;z<=45;z++)for(int x:new int[]{2,45})if(z<18||z>25)for(int h=0;h<4+(z%4==0?1:0);h++)put(w,d,x,d.y+h,z,Blocks.CRACKED_STONE_BRICKS);
        for(int cx:new int[]{8,40})for(int cz:new int[]{8,40}) {
            for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++)if(dx*dx+dz*dz<=38) {
                boolean wall=dx*dx+dz*dz>=24;
                for(int h=0;h<=9;h++)if(wall&&(Math.abs(dx)>2&&Math.abs(dz)>2||h>6)&&!((dx+dz+h)%9==0&&h>6))put(w,d,cx+dx,d.y+h,cz+dz,Blocks.STONE_BRICKS);
                if(wall)put(w,d,cx+dx,d.y+10,cz+dz,Blocks.STONE_BRICK_WALL);
            }
        }
        // East galleries have genuine raised floors and wide stair approaches on both ends.
        fill(w,d,35,d.y+2,3,44,d.y+2,29,Blocks.POLISHED_ANDESITE);
        for(int start:new int[]{2,26})for(int step=0;step<3;step++)for(int dx=-2;dx<=2;dx++) {
            int z=start==2?start+step:start+2-step;
            put(w,d,40+dx,d.y+step-1,z,Blocks.STONE_BRICKS);fill(w,d,40+dx,d.y+step,z,40+dx,d.y+step+3,z,Blocks.AIR);
        }
        // Broad central keep and throne arena remain open to three independent approaches.
        fill(w,d,33,d.y-1,33,46,d.y-1,46,Blocks.CHISELED_STONE_BRICKS);
        for(int x:new int[]{34,46})for(int z:new int[]{34,46})fill(w,d,x,d.y,z,x,d.y+7,z,Blocks.POLISHED_BASALT);
        fill(w,d,35,d.y+7,35,45,d.y+7,45,Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        put(w,d,44,d.y,44,Blocks.GILDED_BLACKSTONE);
        for(int i=0;i<9;i++){var pos=room(d,i);put(w,d,pos.getX()-d.x,pos.getY()+3,pos.getZ()-d.z,Blocks.LANTERN);}
        entrance(w,d,true);entrance(w,d,false);
    }
}
