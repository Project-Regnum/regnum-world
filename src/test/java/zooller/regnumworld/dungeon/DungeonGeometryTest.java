package zooller.regnumworld.dungeon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonGeometryTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void allThemesHaveWalkableRoutesFromEntranceToEveryEncounter() {
        for(String type:List.of("mine","catacombs","bastion"))for(int seed=1;seed<=8;seed++) {
            var d=new DungeonData.Dungeon(type,"minecraft:overworld",0,70,0);d.id=new UUID(0,seed);d.layoutVersion=3;d.frontY=d.backY=type.equals("mine")?80:type.equals("catacombs")?82:70;
            var blocks=DungeonStructures.blueprint(d);assertTrue(blocks.size()>4000,type);
            Set<BlockPos> visited=new HashSet<>();Deque<BlockPos> queue=new ArrayDeque<>();BlockPos start=new BlockPos(24,d.frontY,0);queue.add(start);visited.add(start);
            while(!queue.isEmpty()) {
                var p=queue.removeFirst();
                for(int[] dir:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,1,-1}) {
                    var next=p.offset(dir[0],dy,dir[1]);
                    if(next.getX()<0||next.getX()>47||next.getZ()<0||next.getZ()>47||next.getY()<69||next.getY()>85||visited.contains(next))continue;
                    if(open(blocks,d,next)&&open(blocks,d,next.above())&&!open(blocks,d,next.below())){visited.add(next);queue.add(next);}
                }
            }
            for(int room=0;room<9;room++)assertTrue(visited.contains(DungeonStructures.room(d,room)),type+" room "+room+" is unreachable");
            assertTrue(visited.contains(new BlockPos(24,d.backY,47)),type+" second entrance unreachable");
            assertTrue(blocks.keySet().stream().allMatch(p->p.getX()>=0&&p.getX()<48&&p.getZ()>=0&&p.getZ()<48));
        }
    }
    @Test void forcedMountainSitesHaveContinuousEscapeLaddersAtBothEntrances() {
        for(String type:List.of("mine","catacombs","bastion")) {
            var d=new DungeonData.Dungeon(type,"minecraft:overworld",0,70,0);d.layoutVersion=3;d.frontY=95;d.backY=100;
            var blocks=DungeonStructures.blueprint(d);
            for(boolean front:List.of(true,false)) {
                int z=front?0:47,top=front?d.frontY:d.backY;
                for(int y=d.y;y<=top+1;y++) {
                    assertTrue(blocks.get(new BlockPos(22,y,z)).is(Blocks.LADDER),type+" escape ladder at "+y);
                    assertFalse(open(blocks,d,new BlockPos(21,y,z)),type+" ladder support at "+y);
                }
                for(int z2=front?0:39;z2<=(front?8:47);z2++)assertTrue(open(blocks,d,new BlockPos(24,d.y,z2)),type+" shaft exit");
            }
        }
    }
    private static boolean open(Map<BlockPos,BlockState> blocks,DungeonData.Dungeon d,BlockPos pos) {
        var state=blocks.getOrDefault(pos,d.tier()==3?Blocks.AIR.defaultBlockState():Blocks.STONE.defaultBlockState());
        return state.isAir()||state.getBlock() instanceof LanternBlock||state.getBlock() instanceof LadderBlock;
    }
}
