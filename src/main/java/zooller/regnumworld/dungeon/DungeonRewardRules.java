package zooller.regnumworld.dungeon;

import net.minecraft.nbt.CompoundTag;
import java.util.Random;

/** Stable eligibility snapshot: retries and config reloads cannot reroll a reward. */
final class DungeonRewardRules {
    static void prepare(CompoundTag claim, DungeonData.Dungeon dungeon, DungeonConfig config) {
        if(claim.contains("RewardVersion"))return;
        int power=Math.clamp(claim.getInt("Power"),1,5),tier=dungeon.tier();
        boolean room=claim.contains("Room");
        long seed=dungeon.id.getLeastSignificantBits()^claim.getLong("Cycle")^claim.getUUID("Player").getMostSignificantBits()
                ^(room?claim.getInt("Room")*0x9e3779b97f4a7c15L:0);
        claim.putInt("Power",power);
        claim.putInt("Resources",room?Math.max(1,config.resourceCounts[tier-1]/8):Math.min(64,config.resourceCounts[tier-1]+power-1));
        claim.putInt("Crystals",room?(new Random(seed).nextInt(100)<config.rareChancePercent?1:0):power);
        claim.putLong("Coins",room?0:config.coins[tier-1]*power);
        claim.putInt("RewardVersion",1);
    }
}
