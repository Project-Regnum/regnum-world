package zooller.regnumworld.dungeon;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;
import com.mojang.brigadier.arguments.*;
import java.util.*;
import zooller.regnumworld.RegnumWorld;
import zooller.regnumworld.dungeon.DungeonData.Dungeon;

/** Server-thread authority. Tick work is bounded to registered regions with nearby players. */
public final class Dungeons {
    public static DungeonConfig config;
    public static boolean building;
    public static final ThreadLocal<Boolean> adminWriting=ThreadLocal.withInitial(()->false);
    private static final Map<UUID,Long> bossWindup=new HashMap<>();
    public static boolean admin(net.minecraft.world.entity.player.Player p){return p!=null&&p.isCreative()&&p.hasPermissions(2);}
    public static int clearedRooms(Dungeon d){int n=0;for(int i=0;i<8;i++)if(d.cleared[i])n++;return n;}
    public static boolean suppressNatural(Level level,BlockPos pos){return at(level,pos)!=null;}

    private static final Map<UUID,UUID> presence=new HashMap<>();
    private static final Map<UUID,ServerBossEvent> bars=new HashMap<>();
    private static final Map<String,Set<Long>> fresh=new HashMap<>();
    private static final Set<String> candidates=new HashSet<>();
    public static void initialize() {
        config=DungeonConfig.load();
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{presence.clear();bars.clear();fresh.clear();candidates.clear();DungeonPlacement.clear();DungeonAdmin.clear();bossWindup.clear();});
        ServerChunkEvents.CHUNK_GENERATE.register((world,chunk)->{
            if(!config.generation||!world.dimension().equals(Level.OVERWORLD)) return;
            var set=fresh.computeIfAbsent(world.dimension().location().toString(),k->new HashSet<>());
            set.add(chunk.getPos().toLong());
            int gx=Math.floorDiv(chunk.getPos().x,3)*3, gz=Math.floorDiv(chunk.getPos().z,3)*3;
            if(set.size()>10000) set.clear();
            for(int x=gx;x<gx+3;x++) for(int z=gz;z<gz+3;z++) if(!set.contains(ChunkPos.asLong(x,z))) return;
            String key=gx+":"+gz;
            if(!candidates.add(key)) return;
            long hash=new Random(world.getSeed() ^ ChunkPos.asLong(gx,gz)).nextLong();
            if(Math.floorMod(hash,config.generationOneIn)!=0) return;
            DungeonPlacement.automatic(world,new BlockPos(gx*16,0,gz*16),DungeonTypes.ids().get(Math.floorMod(hash,DungeonTypes.ids().size())));
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(config.protection&&at(world,entity.blockPosition())!=null&&(entity instanceof net.minecraft.world.entity.vehicle.Boat||entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart||entity.getType()==EntityType.ARMOR_STAND||entity.getType()==EntityType.END_CRYSTAL)) {entity.discard();return;}

            boolean owned=false;
            for(String tag:entity.getTags())if(tag.startsWith("regnum_encounter:")) {
                owned=true;
                try {
                    String[] parts=tag.split(":");if(parts.length!=4){entity.discard();return;}
                    Dungeon d=DungeonData.get(world.getServer()).entries.get(UUID.fromString(parts[1]));int room=Integer.parseInt(parts[2]);long cycle=Long.parseLong(parts[3]);
                    if(d==null||room<0||room>8||cycle!=d.cycle||d.cleared[room]||d.bossDefeated){entity.discard();return;}
                    int cap=room==8?1:config.spawnCount+d.tier()+1;
                    long count=d.mobs.entrySet().stream().filter(e->e.getValue()==room&&!e.getKey().equals(entity.getUUID())).count();
                    if(count>=cap||d.mobs.size()>=config.maxMobsPerDungeon&&!d.mobs.containsKey(entity.getUUID())){entity.discard();return;}
                    d.mobs.put(entity.getUUID(),room);d.started[room]=true;DungeonData.get(world.getServer()).setDirty();
                }catch(IllegalArgumentException malformed){entity.discard();}
                break;
            }
            if(!owned&&entity instanceof net.minecraft.world.entity.monster.Enemy&&at(world,entity.blockPosition())!=null)entity.discard();
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{DungeonPlacement.tick(server);tick(server);});
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(!(entity.level() instanceof ServerLevel world)) return;
            DungeonData data=DungeonData.get(world.getServer());
            for(Dungeon d:data.entries.values()) {
                Integer room=d.mobs.remove(entity.getUUID()); if(room==null) continue;
                data.setDirty();bossWindup.remove(entity.getUUID());
                if(room==8) complete(world,d,source.getEntity() instanceof ServerPlayer p?p:null);
                else if(!d.mobs.containsValue(room)) {
                    d.cleared[room]=true;d.started[room]=false;
                    if(source.getEntity() instanceof ServerPlayer recipient&&at(world,recipient.blockPosition())==d){
                        CompoundTag reward=new CompoundTag();reward.putUUID("Player",recipient.getUUID());reward.putLong("Cycle",d.cycle);reward.putInt("Power",d.level);reward.putInt("Room",room);d.pending.add(reward);
                        data.setDirty();world.getServer().overworld().getDataStorage().save();
                    }
                    for(ServerPlayer player:world.players())if(at(world,player.blockPosition())==d)player.displayClientMessage(Component.literal("Encounter "+(room+1)+" cleared · "+clearedRooms(d)+"/8 · "+(d.cleared[4]&&clearedRooms(d)>=config.roomsToUnlockBoss?"Boss unlocked":"Defeat the elite and clear "+config.roomsToUnlockBoss+" rooms to unlock the boss")).withStyle(ChatFormatting.GOLD),false);
                }
                break;
            }
        });
        PlayerBlockBreakEvents.BEFORE.register((world,p,pos,state,be)->(!protectedAt(world,pos)||admin(p)));
        UseBlockCallback.EVENT.register((p,world,hand,hit)-> {
            if(!config.protection||admin(p)) return InteractionResult.PASS;
            var stack=p.getItemInHand(hand);
            boolean terrainItem=stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem || stack.is(Items.FLINT_AND_STEEL)||stack.is(Items.FIRE_CHARGE);
            return terrainItem&&(protectedAt(world,hit.getBlockPos())||protectedAt(world,hit.getBlockPos().relative(hit.getDirection())))?InteractionResult.FAIL:InteractionResult.PASS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->{
            var root=Commands.literal("dungeon").requires(s->s.hasPermission(2));
            DungeonPlacement.commands(root);DungeonAdmin.commands(root);
            root.then(Commands.literal("setlevel").then(Commands.argument("level",IntegerArgumentType.integer(1,5)).executes(c->{var s=c.getSource();Dungeon d=at(s.getLevel(),BlockPos.containing(s.getPosition()));if(d==null)return 0;d.level=IntegerArgumentType.getInteger(c,"level");d.escalationAt=System.currentTimeMillis()-(d.level-1)*config.escalationSeconds*1000;DungeonData.get(s.getServer()).setDirty();return 1;})));
            dispatcher.register(Commands.literal("regnum").then(root));
        });
    }
    public static Dungeon at(Level world,BlockPos pos) {
        if(!(world instanceof ServerLevel s)) return null;
        Dungeon d=DungeonData.get(s.getServer()).find(world.dimension().location().toString(),pos.getX()>>4,pos.getZ()>>4);
        return d!=null&&d.contains(d.dimension,pos.getX(),pos.getY(),pos.getZ())?d:null;
    }
    public static boolean protectedAt(Level world,BlockPos pos) {return config!=null&&config.protection&&at(world,pos)!=null;}
    /** Optional Nations bridge uses vanilla argument types, avoiding a hard mod dependency. */
    public static ListTag snapshot(MinecraftServer server) {ListTag t=new ListTag();DungeonData.get(server).entries.values().forEach(d->t.add(d.save()));return t;}
    public static boolean neutral(MinecraftServer server,String dimension,int cx,int cz) {
        return DungeonData.get(server).find(dimension,cx,cz)!=null;
    }
    private static void tick(MinecraftServer server) {
        if(server.getTickCount()%20!=0)return;
        DungeonData data=DungeonData.get(server);
        for(Dungeon retired:List.copyOf(data.retired)){deliver(server,retired);if(retired.pending.isEmpty()){data.retired.remove(retired);data.setDirty();}}
        long now=System.currentTimeMillis();
        for(ServerPlayer p:server.getPlayerList().getPlayers()) {
            Dungeon d=at(p.level(),p.blockPosition());UUID next=d==null?null:d.id, old=presence.get(p.getUUID());
            if(!Objects.equals(next,old)&&config.announcements) p.displayClientMessage(Component.literal(d==null?"You left the dungeon.":d.name()+" · Tier "+d.tier()+" · "+d.power()+" · PvP ALWAYS ENABLED · clear "+config.roomsToUnlockBoss+" rooms and the elite to awaken the boss").withStyle(ChatFormatting.GOLD),false);
            if(next==null)presence.remove(p.getUUID());else presence.put(p.getUUID(),next);
        }
        presence.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null);
        for(Dungeon d:data.entries.values()) {
            if(server.getTickCount()%100==0)deliver(server,d);
            int level=1+(int)Math.min(4,Math.max(0,(now-d.escalationAt)/(config.escalationSeconds*1000)));
            if(level!=d.level){d.level=level;data.setDirty();}
            ServerLevel world=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(d.dimension)));if(world==null)continue;
            var players=world.players().stream().filter(p->p.isAlive()&&!p.isSpectator()&&d.contains(d.dimension,p.getBlockX(),p.getBlockY(),p.getBlockZ())).toList();
            var bar=bars.computeIfAbsent(d.id,id->new ServerBossEvent(Component.literal(d.name()),BossEvent.BossBarColor.YELLOW,BossEvent.BossBarOverlay.PROGRESS));
            for(ServerPlayer p:List.copyOf(bar.getPlayers()))if(!players.contains(p))bar.removePlayer(p);
            if(players.isEmpty()){
                bar.setVisible(false);
                if(d.lastActive>0&&now-d.lastActive>config.inactiveDespawnSeconds*1000L&&!d.mobs.isEmpty()){
                    clear(world,d);d.cycle++;Arrays.fill(d.started,false);Arrays.fill(d.roomReady,now+config.spawnCooldownSeconds*1000);data.setDirty();
                }continue;
            }
            d.lastActive=now;data.setDirty();
            for(Mob stray:world.getEntitiesOfClass(Mob.class,new AABB(d.x,d.minimumY,d.z,d.x+48,d.y+17,d.z+48),m->m instanceof net.minecraft.world.entity.monster.Enemy&&!m.getTags().contains("regnum_dungeon")))stray.discard();
            boolean loaded=true;for(int cx=d.x>>4;cx<=(d.x+47)>>4;cx++)for(int cz=d.z>>4;cz<=(d.z+47)>>4;cz++)if(!world.getChunkSource().hasChunk(cx,cz)||!world.areEntitiesLoaded(ChunkPos.asLong(cx,cz)))loaded=false;
            if(!loaded)continue;
            // Loaded entity UUIDs survive restarts; missing entities get a cooldown, never an immediate duplicate.
            for(var entry:List.copyOf(d.mobs.entrySet())) {
                Entity mob=world.getEntity(entry.getKey());
                if(mob==null){bossWindup.remove(entry.getKey());d.mobs.remove(entry.getKey());if(entry.getValue()==8){d.bossDefeated=true;d.bossReady=now+config.bossCooldownSeconds*1000;}else {d.started[entry.getValue()]=false;d.roomReady[entry.getValue()]=now+config.spawnCooldownSeconds*1000;}data.setDirty();}
                else if(!d.contains(d.dimension,mob.getBlockX(),mob.getBlockY(),mob.getBlockZ()))mob.teleportTo(DungeonStructures.room(d,entry.getValue()).getX()+0.5,DungeonStructures.floor(d,entry.getValue()),DungeonStructures.room(d,entry.getValue()).getZ()+0.5);
            }
            for(int room=0;room<9;room++)if(d.started[room]&&!d.cleared[room]&&!d.mobs.containsValue(room)){d.started[room]=false;d.roomReady[room]=now+config.spawnCooldownSeconds*1000;data.setDirty();}
            if(d.bossDefeated&&now>=d.bossReady){clear(world,d);d.bossDefeated=false;d.cycle++;d.rewarded.clear();Arrays.fill(d.cleared,false);Arrays.fill(d.started,false);Arrays.fill(d.roomReady,0);data.setDirty();}
            for(int room=0;room<9;room++) {
                final int r=room;BlockPos center=DungeonStructures.room(d,room);int rx=center.getX(),rz=center.getZ();
                if(d.bossDefeated||d.cleared[room]||d.started[room]||d.mobs.containsValue(room)||d.mobs.size()>=config.maxMobsPerDungeon||now<d.roomReady[room]||room==8&&(d.bossDefeated||now<d.bossReady))continue;
                if(room==8&&(!d.cleared[4]||clearedRooms(d)<config.roomsToUnlockBoss))continue;
                if(players.stream().noneMatch(p->p.distanceToSqr(rx,d.y,rz)<(r==8?400:225)))continue;
                int count=room==8?1:config.spawnCount+d.tier()-1+(d.level>=3?1:0);
                if(config.maxMobsPerDungeon-d.mobs.size()<count)continue;
                d.started[room]=true;data.setDirty();
                for(int n=0;n<count&&d.mobs.size()<config.maxMobsPerDungeon;n++) spawn(world,d,room,rx+(n%3)-1,rz+(n/3)-1);
                if(config.announcements)for(ServerPlayer player:players)if(player.distanceToSqr(rx,d.y,rz)<225)player.displayClientMessage(Component.literal(room==8?"The dungeon lord awakens!":"Dungeon guardians awaken.").withStyle(ChatFormatting.GOLD),true);
                data.setDirty();
            }
            LivingEntity boss=d.mobs.entrySet().stream().filter(e->e.getValue()==8).map(e->world.getEntity(e.getKey())).filter(e->e instanceof LivingEntity).map(e->(LivingEntity)e).findFirst().orElse(null);
            for(var encounter:d.mobs.entrySet())if(encounter.getValue()==4&&world.getEntity(encounter.getKey()) instanceof LivingEntity elite)bossMechanic(world,d,elite,players,now);
            bar.setVisible(boss!=null);if(boss!=null){bar.setName(boss.getDisplayName());bar.setProgress(Math.clamp(boss.getHealth()/boss.getMaxHealth(),0,1));players.forEach(bar::addPlayer);bossMechanic(world,d,boss,players,now);}
        }
    }
    private static void spawn(ServerLevel world,Dungeon d,int room,int x,int z) {
        boolean boss=room==8,elite=boss||room==4||d.level>=4;
        EntityType<?> type= d.tier()==1?(room%3==0&&!boss?EntityType.SPIDER:EntityType.ZOMBIE):d.tier()==2?(boss?EntityType.WITHER_SKELETON:room%2==0?EntityType.HUSK:EntityType.SKELETON):(boss?EntityType.PIGLIN_BRUTE:room%2==0?EntityType.VINDICATOR:EntityType.PILLAGER);
        Entity entity=type.create(world);
        if(!(entity instanceof Mob mob))return;
        mob.moveTo(x+0.5,DungeonStructures.floor(d,room),z+0.5,0,0);mob.setPersistenceRequired();
        if(mob instanceof net.minecraft.world.entity.monster.Zombie zombie)zombie.setBaby(false);
        if(mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin)piglin.setImmuneToZombification(true);
        double scale=(1+(d.level-1)*0.22)*config.difficultyMultiplier;
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue((boss?100*d.tier():elite?35*d.tier():20*d.tier())*scale);mob.setHealth(mob.getMaxHealth());
        if(mob.getAttribute(Attributes.ATTACK_DAMAGE)!=null)mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((boss?7:3)*d.tier()*scale);
        mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(d.tier()==1?Items.IRON_HELMET:Items.DIAMOND_HELMET));
        mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(type==EntityType.PILLAGER?Items.CROSSBOW:type==EntityType.SKELETON?Items.BOW:d.tier()==1?Items.IRON_PICKAXE:Items.IRON_AXE));
        if(elite)mob.setItemSlot(EquipmentSlot.CHEST,new ItemStack(d.tier()==3?Items.DIAMOND_CHESTPLATE:Items.IRON_CHESTPLATE));
        for(EquipmentSlot slot:EquipmentSlot.values())mob.setDropChance(slot,0);
        mob.setCustomName(Component.literal(boss?DungeonTypes.get(d.type).boss():room==4?"Relic Guardian · Elite":elite?"Dungeon Elite":"Dungeon Guardian").withStyle(boss?ChatFormatting.GOLD:ChatFormatting.GRAY));mob.setCustomNameVisible(elite);
        if(elite){mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,Integer.MAX_VALUE,0,false,false));mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,Integer.MAX_VALUE,0,false,false));}
        mob.addTag("regnum_dungeon");mob.addTag("regnum_encounter:"+d.id+":"+room+":"+d.cycle); if(world.addFreshEntity(mob))d.mobs.put(mob.getUUID(),room);
    }
    private static void complete(ServerLevel world,Dungeon d,ServerPlayer killer) {
        if(d.bossDefeated)return;
        d.cleared[8]=true;d.bossDefeated=true;d.bossReady=System.currentTimeMillis()+config.bossCooldownSeconds*1000;d.escalationAt=System.currentTimeMillis();int power=d.level;d.level=1;
        DungeonData data=DungeonData.get(world.getServer());data.setDirty();
        for(ServerPlayer p:world.players())if(p.isAlive()&&!p.isSpectator()&&d.contains(d.dimension,p.getBlockX(),p.getBlockY(),p.getBlockZ())&&p.distanceToSqr(d.x+40,d.y,d.z+40)<576) {
            if(!d.rewarded.add(p.getUUID()))continue;
            CompoundTag claim=new CompoundTag();claim.putUUID("Player",p.getUUID());claim.putLong("Cycle",d.cycle);claim.putInt("Power",power);d.pending.add(claim);
            p.sendSystemMessage(Component.literal(d.name()+" cleared! Rewards will arrive when inventory space is available.").withStyle(ChatFormatting.GOLD));
        }
        data.setDirty();world.getServer().overworld().getDataStorage().save();deliver(world.getServer(),d);
    }
    private static void deliver(MinecraftServer server,Dungeon d) {
        for(CompoundTag claim:List.copyOf(d.pending)) {
            ServerPlayer p=server.getPlayerList().getPlayer(claim.getUUID("Player"));if(p==null||!p.isAlive()||p.isSpectator())continue;
            int power=claim.getInt("Power");long cycle=claim.getLong("Cycle");
            boolean roomReward=claim.contains("Room");
            List<ItemStack> loot=new ArrayList<>();
            loot.add(new ItemStack(DungeonItems.material(d.type),d.tier()+power+2));
            if(!roomReward)loot.add(new ItemStack(DungeonItems.trophy(d.type)));
            if(!roomReward)loot.add(new ItemStack(DungeonItems.CORRUPTED_CRYSTAL,Math.max(1,power)));
            var table=server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse(DungeonTypes.get(d.type).lootTable())));
            var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(p.serverLevel())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,p.position())
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            if(!roomReward)loot.addAll(table.getRandomItems(params,d.id.getLeastSignificantBits()^cycle^p.getUUID().getMostSignificantBits()));
            boolean paid=false;
            try {Class<?> api=Class.forName("dev.regnum.economy.money.DungeonRewards");paid=(boolean)api.getMethod("grant",ServerPlayer.class,String.class,long.class,List.class).invoke(null,p,d.id+":"+cycle+(roomReward?":room:"+claim.getInt("Room"):""),roomReward?0L:config.coins[d.tier()-1]*power,loot);}
            catch(ClassNotFoundException absent) {
                // At-most-once standalone fallback: consume eligibility durably before handing out vanilla loot.
                d.pending.remove(claim);DungeonData.get(server).setDirty();server.overworld().getDataStorage().save();
                for(ItemStack stack:loot)if(!p.addItem(stack))p.drop(stack,false);server.getPlayerList().saveAll();paid=true;
            }
            catch(Exception e){RegnumWorld.LOGGER.error("Dungeon reward failed",e);}
            if(paid){d.pending.remove(claim);DungeonData.get(server).setDirty();p.sendSystemMessage(Component.literal(roomReward?"Dungeon crafting materials received.":"Dungeon relics, trophy and rewards received.").withStyle(ChatFormatting.GOLD));}
        }
    }
    private static void bossMechanic(ServerLevel world,Dungeon d,LivingEntity boss,List<ServerPlayer> players,long now) {
        long due=bossWindup.getOrDefault(boss.getUUID(),0L);
        if(due==0){bossWindup.put(boss.getUUID(),now+8000);return;}
        if(now>=due&&now<due+2000){for(ServerPlayer p:players)p.displayClientMessage(Component.literal(d.tier()==1?"Foreman winds up a crushing blow — move away!":d.tier()==2?"The crypt fills with a cursed chill — spread out!":"Castellan prepares a ground slam — leave the arena center!").withStyle(ChatFormatting.RED),true);bossWindup.put(boss.getUUID(),-(now+2000));}
        else if(due<0&&now>=-due){
            for(ServerPlayer p:players)if(p.distanceToSqr(boss)<(d.tier()==2?64:36)){
                p.hurt(world.damageSources().mobAttack(boss),3*d.tier());
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(d.tier()==2?net.minecraft.world.effect.MobEffects.WEAKNESS:net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,80,1));
                if(d.tier()==3)p.knockback(1,boss.getX()-p.getX(),boss.getZ()-p.getZ());
            }bossWindup.put(boss.getUUID(),now+8000);
        }else if(due>0&&now>=due+2000)bossWindup.put(boss.getUUID(),now+8000);
    }
    static void clear(ServerLevel world,Dungeon d) {
        for(UUID id:d.mobs.keySet()){bossWindup.remove(id);Entity e=world.getEntity(id);if(e!=null)e.discard();}d.mobs.clear();
        bossWindup.remove(d.id);
        ServerBossEvent bar=bars.remove(d.id);if(bar!=null)bar.removeAllPlayers();
    }
}
