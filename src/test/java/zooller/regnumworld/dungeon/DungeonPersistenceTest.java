package zooller.regnumworld.dungeon;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DungeonPersistenceTest {
    @Test void restartRetainsEncounterAndPendingRewardWithoutCrossDimensionTerritory() {
        DungeonData original=new DungeonData();
        var d=new DungeonData.Dungeon("mine","minecraft:overworld",-48,70,-96);
        UUID mob=UUID.randomUUID(),player=UUID.randomUUID();d.layoutVersion=2;d.cleared[4]=true;d.started[2]=true;d.lastActive=123;d.minimumY=40;d.frontY=42;d.mobs.put(mob,8);d.level=4;d.cycle=17;d.bossReady=123456;d.roomReady[3]=891011;
        CompoundTag claim=new CompoundTag();claim.putUUID("Player",player);claim.putLong("Cycle",17);claim.putInt("Power",4);d.pending.add(claim);d.rewarded.add(player);
        original.register(d);var loaded=DungeonData.load(original.save(new CompoundTag(),null),null);
        var recovered=loaded.entries.get(d.id);
        assertTrue(recovered.cleared[4]);assertTrue(recovered.started[2]);assertEquals(123,recovered.lastActive);assertEquals(2,recovered.layoutVersion);
        assertEquals(40,recovered.minimumY);assertEquals(42,recovered.frontY);assertTrue(recovered.contains("minecraft:overworld",-48,41,-96));
        assertEquals(8,recovered.mobs.get(mob));assertEquals(4,recovered.level);assertEquals(17,recovered.cycle);
        assertEquals(123456,recovered.bossReady);assertEquals(891011,recovered.roomReady[3]);assertEquals(claim,recovered.pending.getFirst());assertTrue(recovered.rewarded.contains(player));
        assertSame(recovered,loaded.find("minecraft:overworld",-3,-6));assertNull(loaded.find("minecraft:the_nether",-3,-6));
        assertTrue(recovered.contains("minecraft:overworld",-48,70,-96));assertFalse(recovered.contains("minecraft:overworld",0,70,-96));assertFalse(recovered.contains("minecraft:overworld",-48,200,-96));
        loaded.remove(recovered);assertNull(loaded.find("minecraft:overworld",-3,-6));
    }
    @Test void legacyDungeonKeepsOriginalProtectionHeight() {
        DungeonData original=new DungeonData();var dungeon=new DungeonData.Dungeon("catacombs","minecraft:overworld",0,70,0);original.register(dungeon);
        var saved=original.save(new CompoundTag(),null);
        saved.getList("Dungeons",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("MinimumY");
        var loaded=DungeonData.load(saved,null).entries.get(dungeon.id);
        assertEquals(62,loaded.minimumY);assertTrue(loaded.contains("minecraft:overworld",0,62,0));assertFalse(loaded.contains("minecraft:overworld",0,61,0));
    }
    @Test void removedDungeonKeepsPendingRewardsWithoutReservingTerritory() {
        DungeonData original=new DungeonData();var d=new DungeonData.Dungeon("mine","minecraft:overworld",0,70,0);
        var reward=new CompoundTag();reward.putUUID("Player",UUID.randomUUID());reward.putLong("Cycle",3);d.pending.add(reward);original.retired.add(d);
        var loaded=DungeonData.load(original.save(new CompoundTag(),null),null);
        assertTrue(loaded.entries.isEmpty());assertNull(loaded.find("minecraft:overworld",0,0));assertEquals(reward,loaded.retired.getFirst().pending.getFirst());
    }
}
