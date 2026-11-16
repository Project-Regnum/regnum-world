package zooller.regnumworld.dungeon;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DungeonPlacementTest {
    @Test void searchHonorsBudgetAndNegativeChunkAlignment() {
        var points=DungeonPlacement.candidates(new BlockPos(-1,70,-17),true,192,16);
        assertEquals(new BlockPos(-16,0,-32),points.getFirst());
        assertEquals(16,points.size());assertEquals(points.size(),points.stream().distinct().count());
        for(var point:points){assertEquals(0,Math.floorMod(point.getX(),16));assertEquals(0,Math.floorMod(point.getZ(),16));assertTrue(Math.abs(point.getX()+16)<=192);assertTrue(Math.abs(point.getZ()+32)<=192);}
        assertEquals(1,DungeonPlacement.candidates(BlockPos.ZERO,false,192,16).size());
        assertEquals(1,DungeonPlacement.candidates(BlockPos.ZERO,true,0,16).size());
    }
    @Test void forceBypassesSpacingButNeverDungeonOverlap() {
        assertNotNull(DungeonPlacement.dungeonConflict(0,0,32,32,"existing",true,512));
        assertNotNull(DungeonPlacement.dungeonConflict(0,0,48,0,"existing",false,512));
        assertNull(DungeonPlacement.dungeonConflict(0,0,48,0,"existing",true,512));
        assertNull(DungeonPlacement.dungeonConflict(0,0,512,0,"existing",false,512));
        assertTrue(DungeonPlacement.overlaps(-48,-48,-1,-1));
        assertFalse(DungeonPlacement.overlaps(-48,-48,0,0));
    }
}
