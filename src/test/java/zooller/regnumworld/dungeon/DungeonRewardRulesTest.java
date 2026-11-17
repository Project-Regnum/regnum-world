package zooller.regnumworld.dungeon;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DungeonRewardRulesTest {
    private CompoundTag claim(boolean room) {
        var c=new CompoundTag();c.putUUID("Player",new UUID(5,9));c.putLong("Cycle",17);c.putInt("Power",3);
        if(room)c.putInt("Room",4);return c;
    }
    @Test void bossValueAndRoomRarityRespectConfigAndStayStableAcrossRetries() {
        var config=new DungeonConfig();var dungeon=new DungeonData.Dungeon("bastion","minecraft:overworld",0,70,0);
        var boss=claim(false);var room=claim(true);config.rareChancePercent=100;
        DungeonRewardRules.prepare(boss,dungeon,config);DungeonRewardRules.prepare(room,dungeon,config);
        assertEquals(3600,boss.getLong("Coins"));assertEquals(26,boss.getInt("Resources"));assertEquals(3,boss.getInt("Crystals"));
        assertEquals(0,room.getLong("Coins"));assertEquals(3,room.getInt("Resources"));assertEquals(1,room.getInt("Crystals"));
        var snapshot=boss.copy();config.coins[2]=1;config.resourceCounts[2]=1;
        DungeonRewardRules.prepare(boss,dungeon,config);assertEquals(snapshot,boss);
        var common=claim(true);config.rareChancePercent=0;DungeonRewardRules.prepare(common,dungeon,config);assertEquals(0,common.getInt("Crystals"));
    }
}
