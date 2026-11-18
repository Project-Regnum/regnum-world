package zooller.regnumworld.dungeon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class DungeonData extends SavedData {
    public final List<Dungeon> retired=new ArrayList<>();
    public final Map<UUID, Dungeon> entries = new LinkedHashMap<>();
    private final Map<String,Map<Long,Dungeon>> regions=new HashMap<>();
    public Dungeon find(String dimension,int cx,int cz) {return regions.getOrDefault(dimension,Map.of()).get(net.minecraft.world.level.ChunkPos.asLong(cx,cz));}
    public void register(Dungeon d) {
        if(entries.containsKey(d.id))throw new IllegalStateException("Duplicate dungeon UUID");
        var index=regions.computeIfAbsent(d.dimension,k->new HashMap<>());
        // Validate the complete reservation before publishing any registry or region entries.
        for(int x=d.x>>4;x<=(d.x+47)>>4;x++)for(int z=d.z>>4;z<=(d.z+47)>>4;z++)
            if(index.containsKey(net.minecraft.world.level.ChunkPos.asLong(x,z)))throw new IllegalStateException("Overlapping dungeon bounds");
        entries.put(d.id,d);
        for(int x=d.x>>4;x<=(d.x+47)>>4;x++)for(int z=d.z>>4;z<=(d.z+47)>>4;z++)
            index.put(net.minecraft.world.level.ChunkPos.asLong(x,z),d);
        setDirty();
    }
    public void remove(Dungeon d) {entries.remove(d.id);regions.getOrDefault(d.dimension,Map.of()).values().removeIf(v->v.id.equals(d.id));setDirty();}
    public static DungeonData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(DungeonData::new,DungeonData::load,null),"regnum_dungeons");
    }
    public static final class Dungeon {
        public UUID id = UUID.randomUUID();
        public String type, dimension;
        public int x,y,z,frontY,backY,level=1;
        public int minimumY, maximumY;
        public int layoutVersion=1;
        public long lastActive;
        public final boolean[] cleared=new boolean[9], started=new boolean[9];
        public long escalationAt, bossReady, cycle;
        public boolean bossDefeated;
        public final long[] roomReady = new long[9];
        public final Map<UUID,Integer> mobs = new HashMap<>();
        public final List<CompoundTag> pending = new ArrayList<>();
        public final Set<UUID> rewarded = new HashSet<>();
        public Dungeon(String type,String dimension,int x,int y,int z) {
            this.type=type; this.dimension=dimension; this.x=x;this.y=y;this.z=z;this.frontY=y;this.backY=y;this.minimumY=y-8;this.maximumY=y+16;
            escalationAt=System.currentTimeMillis();
        }
        public int tier() { return Dungeons.config==null?DungeonTypes.get(type).tier():Math.clamp(Dungeons.config.tiers.getOrDefault(type,DungeonTypes.get(type).tier()),1,3); }
        public int partySize() {return Dungeons.config==null?DungeonTypes.get(type).partySize():Math.clamp(Dungeons.config.partySizes.getOrDefault(type,DungeonTypes.get(type).partySize()),1,20);}
        public String name() {return DungeonTypes.get(type).name();}
        public String state() { return bossDefeated ? "Recovering" : mobs.isEmpty() ? "Ready" : "Contested"; }
        public String power() { return new String[]{"Dormant","Stirring","Dangerous","Awakened","Critical"}[level-1]; }
        public boolean contains(String dim,int px,int py,int pz) {
            return dimension.equals(dim)&&px>=x&&px<x+48&&pz>=z&&pz<z+48&&py>=minimumY&&py<=maximumY;
        }
        CompoundTag atlasMarker() {
            CompoundTag t=new CompoundTag(); t.putUUID("Id",id);t.putString("Type",type);t.putString("Name",name());t.putInt("Tier",tier());t.putInt("PartySize",partySize());t.putString("Dimension",dimension);
            t.putInt("X",x);t.putInt("Y",y);t.putInt("Z",z);
            t.putIntArray("Bounds",new int[]{x,minimumY,z,x+47,maximumY,z+47});
            t.putIntArray("Entrances",new int[]{x+24,frontY,z,x+24,backY,z+47});t.putString("Activity",state());t.putInt("Level",level);
            t.putBoolean("Defeated",bossDefeated);
            return t;
        }
        public CompoundTag save() {
            CompoundTag t = atlasMarker();
            t.putInt("FrontY",frontY);t.putInt("BackY",backY);
            t.putInt("LayoutVersion",layoutVersion);t.putLong("LastActive",lastActive);
            int[] clear=new int[9],start=new int[9];for(int i=0;i<9;i++){clear[i]=cleared[i]?1:0;start[i]=started[i]?1:0;}t.putIntArray("Cleared",clear);t.putIntArray("Started",start);
            t.putInt("MaximumY",maximumY);t.putInt("MinimumY",minimumY);t.putLong("Escalation",escalationAt);t.putLong("BossReady",bossReady);t.putLong("Cycle",cycle);
            t.putLongArray("Rooms",roomReady); ListTag ms=new ListTag();
            mobs.forEach((uuid,room)->{CompoundTag m=new CompoundTag();m.putUUID("Id",uuid);m.putInt("Room",room);ms.add(m);}); t.put("Mobs",ms);
            ListTag rs=new ListTag(); for(UUID uuid:rewarded) {CompoundTag r=new CompoundTag();r.putUUID("Id",uuid);rs.add(r);} t.put("Rewarded",rs); ListTag pendingTags=new ListTag();pending.forEach(p->pendingTags.add(p.copy()));t.put("Pending",pendingTags);return t;
        }
    }
    static DungeonData load(CompoundTag tag,HolderLookup.Provider registries) {
        DungeonData data=new DungeonData();
        for(Tag item:tag.getList("Dungeons",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)item;
            Dungeon d=new Dungeon(t.getString("Type"),t.getString("Dimension"),t.getInt("X"),t.getInt("Y"),t.getInt("Z"));
            if (!DungeonTypes.ids().contains(d.type)) throw new IllegalStateException("Unknown dungeon type");
            d.layoutVersion=t.contains("LayoutVersion")?t.getInt("LayoutVersion"):1;d.lastActive=t.getLong("LastActive");
            int[] clear=t.getIntArray("Cleared"),start=t.getIntArray("Started");for(int i=0;i<9;i++){d.cleared[i]=i<clear.length&&clear[i]!=0;d.started[i]=i<start.length&&start[i]!=0;}
            d.maximumY=t.contains("MaximumY")?Math.max(d.y+16,t.getInt("MaximumY")):Math.max(d.y+16,Math.max(t.getInt("FrontY"),t.getInt("BackY"))+5);
            d.minimumY=t.contains("MinimumY")?t.getInt("MinimumY"):d.y-8;
            d.frontY=t.contains("FrontY")?t.getInt("FrontY"):d.y;d.backY=t.contains("BackY")?t.getInt("BackY"):d.y;
            d.id=t.getUUID("Id");d.level=Math.clamp(t.getInt("Level"),1,5);d.escalationAt=t.getLong("Escalation");
            d.bossReady=t.getLong("BossReady");d.cycle=t.getLong("Cycle");d.bossDefeated=t.getBoolean("Defeated");
            long[] rooms=t.getLongArray("Rooms");System.arraycopy(rooms,0,d.roomReady,0,Math.min(9,rooms.length));
            for(Tag m:t.getList("Mobs",Tag.TAG_COMPOUND)) d.mobs.put(((CompoundTag)m).getUUID("Id"),((CompoundTag)m).getInt("Room"));
            for(Tag r:t.getList("Rewarded",Tag.TAG_COMPOUND)) d.rewarded.add(((CompoundTag)r).getUUID("Id"));
            for(Tag p:t.getList("Pending",Tag.TAG_COMPOUND)) d.pending.add(((CompoundTag)p).copy());
            data.register(d);
        }
        for(Tag raw:tag.getList("Retired",Tag.TAG_COMPOUND)) {
            CompoundTag wrapper=new CompoundTag();ListTag single=new ListTag();single.add(raw.copy());wrapper.put("Dungeons",single);
            data.retired.addAll(load(wrapper,registries).entries.values());
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        ListTag list=new ListTag();entries.values().forEach(d->list.add(d.save()));tag.put("Dungeons",list);ListTag retiredTags=new ListTag();retired.forEach(d->retiredTags.add(d.save()));tag.put("Retired",retiredTags);return tag;
    }
}
