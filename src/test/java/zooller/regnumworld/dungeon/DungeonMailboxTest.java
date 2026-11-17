package zooller.regnumworld.dungeon;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class DungeonMailboxTest {
    @TempDir Path directory;
    @Test void durableFenceSurvivesRetryAndPreservesOriginalPayload() throws Exception {
        Path marker=directory.resolve("delivery.snbt");DungeonMailbox.reserve(marker,"original payload");
        assertThrows(FileAlreadyExistsException.class,()->DungeonMailbox.reserve(marker,"duplicate"));
        assertEquals("original payload",Files.readString(marker));
    }
    @Test void heldClaimsAndLootPersistAcrossRemovalAndRestart(){
        var data=new DungeonData();var d=new DungeonData.Dungeon("mine","minecraft:overworld",0,70,0);
        var claim=new CompoundTag();claim.putUUID("Player",UUID.randomUUID());claim.putUUID("ClaimId",UUID.randomUUID());claim.putBoolean("DeliveryHeld",true);claim.put("Loot",new net.minecraft.nbt.ListTag());d.pending.add(claim);data.retired.add(d);
        var restored=DungeonData.load(data.save(new CompoundTag(),null),null).retired.getFirst().pending.getFirst();
        assertEquals(claim,restored);assertFalse(DungeonMailbox.retryable(restored));
        assertEquals(DungeonMailbox.receipt(d,claim),DungeonMailbox.receipt(d,restored));
        var room=claim.copy();room.putInt("Room",0);assertNotEquals(DungeonMailbox.receipt(d,claim),DungeonMailbox.receipt(d,room));
    }
}
