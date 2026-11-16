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
        for(String type:List.of("mine","catacombs","bastion")) {
            var d=new DungeonData.Dungeon(type,"minecraft:overworld",0,70,0);d.id=UUID.fromString("00000000-0000-0000-0000-000000000001");d.layoutVersion=2;d.frontY=d.backY=type.equals("mine")?80:type.equals("catacombs")?82:70;
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
    private static boolean open(Map<BlockPos,BlockState> blocks,DungeonData.Dungeon d,BlockPos pos) {
        var state=blocks.getOrDefault(pos,d.tier()==3?Blocks.AIR.defaultBlockState():Blocks.STONE.defaultBlockState());
        return state.isAir()||state.getBlock() instanceof LanternBlock||state.getBlock() instanceof LadderBlock;
    }
}
