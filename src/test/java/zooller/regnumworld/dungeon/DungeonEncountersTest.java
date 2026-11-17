package zooller.regnumworld.dungeon;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DungeonEncountersTest {
    @Test void missingBossRetriesAfterCooldownWithoutDefeatOrRewards(){
        var d=new DungeonData.Dungeon("mine","minecraft:overworld",0,70,0);d.started[8]=true;
        DungeonEncounters.missing(d,8,100,500);
        assertFalse(d.bossDefeated);assertFalse(d.cleared[8]);assertFalse(d.started[8]);
        assertEquals(600,d.bossReady);assertTrue(d.pending.isEmpty());
    }
    @Test void failedSpawnAndPartialSpawnHaveBoundedRecovery(){
        var d=new DungeonData.Dungeon("mine","minecraft:overworld",0,70,0);d.started[2]=true;
        DungeonEncounters.spawned(d,2,100,500);assertFalse(d.started[2]);assertEquals(600,d.roomReady[2]);
        d.mobs.put(UUID.randomUUID(),2);DungeonEncounters.spawned(d,2,700,500);assertTrue(d.started[2]);
        DungeonEncounters.missing(d,2,800,500);assertTrue(d.started[2]);assertFalse(d.cleared[2]);
    }
}
