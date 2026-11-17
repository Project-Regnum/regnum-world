package zooller.regnumworld.dungeon;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

/** Restart to apply changes. Durations are seconds of wall time, including offline time. */
public final class DungeonConfig {
    public boolean generation = true, protection = true, announcements = true;
    public int generationOneIn = 64, minimumSpacing = 512, spawnCount = 2;
    public long spawnCooldownSeconds = 300, bossCooldownSeconds = 1800, escalationSeconds = 7200;
    public double difficultyMultiplier = 1;
    public long[] coins = {150, 450, 1200};
    public int[] resourceCounts = {8, 16, 24};
    public java.util.Map<String,Integer> tiers = new java.util.LinkedHashMap<>(java.util.Map.of("mine",1,"catacombs",2,"bastion",3));
    public java.util.Map<String,Integer> partySizes = new java.util.LinkedHashMap<>(java.util.Map.of("mine",2,"catacombs",3,"bastion",4));
    public int placementSearchRadius = 192, placementCandidates = 16, placementChunkLimit = 96;
    public int placementChunksPerTick = 1, placementMaxElevation = 24, placementMaxVerticalSpan = 64, placementFoundationDepth = 16;
    public int maxMobsPerDungeon=24, inactiveDespawnSeconds=180, roomsToUnlockBoss=4;
    public int rareChancePercent = 25;
    public static DungeonConfig load() {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("regnum-dungeons.json");
        try {
            DungeonConfig c = Files.exists(path) ? gson.fromJson(Files.readString(path), DungeonConfig.class) : new DungeonConfig();
            if (c == null || c.coins == null || c.resourceCounts == null || c.tiers == null || c.partySizes == null || c.coins.length != 3 || c.resourceCounts.length != 3) throw new IllegalArgumentException("Expected three tier rewards");
            c.rareChancePercent=Math.clamp(c.rareChancePercent,0,100);
            c.maxMobsPerDungeon=Math.clamp(c.maxMobsPerDungeon,4,64);c.inactiveDespawnSeconds=Math.clamp(c.inactiveDespawnSeconds,30,3600);c.roomsToUnlockBoss=Math.clamp(c.roomsToUnlockBoss,1,8);
            c.placementSearchRadius=Math.clamp(c.placementSearchRadius,0,512);
            c.placementCandidates=Math.clamp(c.placementCandidates,1,64);c.placementChunkLimit=Math.clamp(c.placementChunkLimit,9,256);
            c.placementChunksPerTick=Math.clamp(c.placementChunksPerTick,1,4);c.placementMaxElevation=Math.clamp(c.placementMaxElevation,1,32);
            c.placementMaxVerticalSpan=Math.clamp(c.placementMaxVerticalSpan,16,96);c.placementFoundationDepth=Math.clamp(c.placementFoundationDepth,1,32);
            c.generationOneIn = Math.max(1, c.generationOneIn); c.minimumSpacing = Math.max(64,c.minimumSpacing);
            c.spawnCount = Math.clamp(c.spawnCount,1,8); c.spawnCooldownSeconds = Math.max(30,c.spawnCooldownSeconds);
            c.bossCooldownSeconds = Math.max(60,c.bossCooldownSeconds); c.escalationSeconds = Math.max(60,c.escalationSeconds);
            c.difficultyMultiplier = Math.clamp(c.difficultyMultiplier,0.1,10);
            for (int i=0;i<3;i++) { c.coins[i]=Math.clamp(c.coins[i],0,1_000_000); c.resourceCounts[i]=Math.clamp(c.resourceCounts[i],1,64); }
            Files.writeString(path,gson.toJson(c)); return c;
        } catch (Exception e) { throw new IllegalStateException("Cannot load dungeon configuration: " + path,e); }
    }
}
