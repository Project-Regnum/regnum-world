package zooller.regnumworld.dungeon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Bounded placement jobs. No inhabited-time gate for administrators; no force bypass for safety. */
final class DungeonPlacement {
    record Plan(String type,int x,int y,int z,int front,int back,int bottom,int top,long signature) {
        String describe() {return type+" at "+x+" "+y+" "+z+"; 48×48 footprint; terrain writes Y "+bottom+".."+top+"; entrances "+(x+24)+" "+front+" "+z+" and "+(x+24)+" "+back+" "+(z+47);}
    }
    record Check(Plan plan,List<String> failures) {boolean valid(){return failures.isEmpty();}}
    record Preview(CommandSourceStack source,Plan plan,boolean force,long expires) {}
    private static final Deque<Job> JOBS=new ArrayDeque<>();

    private static final TicketType<ChunkPos> TICKET=TicketType.create("regnum_dungeon_placement",Comparator.comparingLong(ChunkPos::toLong),200);
    private static boolean loading;
    static void clear(){JOBS.clear();loading=false;}
    private static DungeonConfig config(){return Dungeons.config;}
    static void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        for(String action:List.of("generate","preview","validate")) {
            var verb=Commands.literal(action);
            for(String type:DungeonTypes.ids()) {
                var node=Commands.literal(type).executes(c->request(c.getSource(),type,BlockPos.containing(c.getSource().getPosition()),false,action,action.equals("generate")));
                node.then(Commands.literal("here").executes(c->request(c.getSource(),type,BlockPos.containing(c.getSource().getPosition()),false,action,false)));
                node.then(Commands.literal("force").executes(c->request(c.getSource(),type,BlockPos.containing(c.getSource().getPosition()),true,action,false)));
                node.then(Commands.argument("x",IntegerArgumentType.integer(-29999984,29999936)).then(
                    Commands.argument("z",IntegerArgumentType.integer(-29999984,29999936))
                    .executes(c->request(c.getSource(),type,new BlockPos(IntegerArgumentType.getInteger(c,"x"),0,IntegerArgumentType.getInteger(c,"z")),false,action,false))
                    .then(Commands.literal("force").executes(c->request(c.getSource(),type,new BlockPos(IntegerArgumentType.getInteger(c,"x"),0,IntegerArgumentType.getInteger(c,"z")),true,action,false)))));
                verb.then(node);
            }root.then(verb);
        }
    }
    private static String actor(CommandSourceStack source){return source.getEntity()==null?source.getTextName():source.getEntity().getStringUUID();}
    private static int request(CommandSourceStack source,String type,BlockPos pos,boolean force,String action,boolean search) {
        return enqueue(new Job(source.getLevel(),type,pos,force,false,search,source,action,null));
    }
    static void automatic(ServerLevel world,BlockPos pos,String type) {
        // Chunk loads made by a placement job must not recursively schedule more generation.
        if(!loading&&JOBS.isEmpty())enqueue(new Job(world,type,pos,false,true,true,null,"generate",null));
    }
    private static int enqueue(Job job) {
        if(JOBS.size()>=4||job.source!=null&&JOBS.stream().anyMatch(j->j.source!=null&&actor(j.source).equals(actor(job.source)))) {
            if(job.source!=null)job.source.sendFailure(Component.literal("A placement job is already running for you, or the queue is full."));return 0;
        }
        JOBS.add(job);if(job.source!=null)job.source.sendSuccess(()->Component.literal("Checking "+job.points.size()+" bounded placement candidate(s); explicit chunk budget "+config().placementChunkLimit+". Coordinates are aligned to chunk origins."),false);return 1;
    }
    static void tick(MinecraftServer server) {

        Job job=JOBS.peek();if(job==null)return;
        try {if(job.step()) {job.release();JOBS.remove();}}
        catch(Exception e) {job.release();JOBS.remove();if(job.source!=null)job.source.sendFailure(Component.literal("Placement stopped safely: "+e.getMessage()));zooller.regnumworld.RegnumWorld.LOGGER.error("Dungeon placement failed",e);}
    }
    static List<BlockPos> candidates(BlockPos origin,boolean search,int radius,int limit) {
        int x=Math.floorDiv(origin.getX(),16)*16,z=Math.floorDiv(origin.getZ(),16)*16;
        var points=new ArrayList<BlockPos>();points.add(new BlockPos(x,0,z));
        if(search)for(int r=48;r<=radius&&points.size()<limit;r+=48) {
            for(int dx=-r;dx<=r&&points.size()<limit;dx+=48)for(int dz=-r;dz<=r&&points.size()<limit;dz+=48)
                if(Math.abs(dx)==r||Math.abs(dz)==r)points.add(new BlockPos(x+dx,0,z+dz));
        }return List.copyOf(points);
    }
    private static final class Job {
        final ServerLevel world;final String type,action;final boolean force,automatic;final CommandSourceStack source;final Plan expected;
        final List<BlockPos> points;final Set<Long> touched=new HashSet<>(),tickets=new HashSet<>();final Map<String,Integer> reasons=new LinkedHashMap<>();
        final long deadline=System.currentTimeMillis()+120000;
        int candidate;List<ChunkPos> required;boolean references;
        Job(ServerLevel world,String type,BlockPos origin,boolean force,boolean automatic,boolean search,CommandSourceStack source,String action,Plan expected) {
            this.world=world;this.type=type;this.force=force;this.automatic=automatic;this.source=source;this.action=action;this.expected=expected;
            points=candidates(origin,search,config().placementSearchRadius,config().placementCandidates);
        }
        void release(){for(long key:tickets){var cp=new ChunkPos(key);world.getChunkSource().removeRegionTicket(TICKET,cp,0,cp);}tickets.clear();}
        boolean reject(List<String> failures) {
            for(String failure:failures)reasons.merge(failure,1,Integer::sum);
            if(source!=null&&candidate==0)for(String failure:failures.stream().limit(8).toList())source.sendFailure(Component.literal("Placement refused at "+points.get(candidate).getX()+", "+points.get(candidate).getZ()+": "+failure));
            candidate++;required=null;references=false;release();return false;
        }
        boolean step() {
            if(System.currentTimeMillis()>deadline){fail("Placement timed out after 120 seconds.");return true;}
            if(candidate>=points.size()){fail("No safe location in "+points.size()+" candidate(s). "+String.join("; ",reasons.keySet().stream().limit(6).toList()));return true;}
            BlockPos origin=points.get(candidate);int x=origin.getX(),z=origin.getZ();
            var early=regionFailures(world,x,z,force);
            if(!early.isEmpty())return reject(early);
            if(required==null){required=new ArrayList<>();for(int cx=x>>4;cx<=(x+47)>>4;cx++)for(int cz=z>>4;cz<=(z+47)>>4;cz++)required.add(new ChunkPos(cx,cz));}
            for(long key:tickets){var cp=new ChunkPos(key);world.getChunkSource().addRegionTicket(TICKET,cp,0,cp);}
            int loads=0;
            for(ChunkPos cp:required)if(!tickets.contains(cp.toLong())) {
                if(!touched.contains(cp.toLong())&&touched.size()>=config().placementChunkLimit){fail("Chunk budget exhausted ("+config().placementChunkLimit+"). Last failures: "+String.join("; ",reasons.keySet().stream().limit(4).toList()));return true;}
                if(loads>=config().placementChunksPerTick)return false;
                world.getChunkSource().addRegionTicket(TICKET,cp,0,cp);tickets.add(cp.toLong());touched.add(cp.toLong());
                loading=true;try{world.getChunk(cp.x,cp.z);}catch(RuntimeException e){throw new IllegalStateException("cannot load chunk ("+cp.x+", "+cp.z+"): "+e.getMessage(),e);}finally{loading=false;}loads++;
            }
            if(!references) {
                var extra=new LinkedHashSet<ChunkPos>();
                for(ChunkPos cp:required)for(var refs:world.getChunk(cp.x,cp.z).getAllReferences().values())for(long key:refs)extra.add(new ChunkPos(key));
                extra.removeAll(required);if(required.size()+extra.size()>config().placementChunkLimit)return reject(List.of("too many structure-reference chunks to validate safely"));
                required.addAll(extra);references=true;if(!extra.isEmpty())return false;
            }
            // Entity files are asynchronous even when terrain is FULL; inspect decorations/pets only after they arrive.
            for(int cx=x>>4;cx<=(x+47)>>4;cx++)for(int cz=z>>4;cz<=(z+47)>>4;cz++)if(!world.areEntitiesLoaded(ChunkPos.asLong(cx,cz)))return false;
            Check check=inspect(world,type,x,z,force,automatic,required);
            if(!check.valid())return reject(check.failures());
            Plan plan=check.plan();
            if(expected!=null&&(!expected.equals(plan))){fail("Terrain changed since preview; run preview again before confirming.");return true;}
            if(action.equals("validate")){say("Placement valid: "+plan.describe()+". No blocks changed.");return true;}
            if(action.equals("preview")||force&&!action.equals("confirm")) {
                DungeonAdmin.confirm(source,"Place "+plan.describe()+"; replace validated terrain (no restoration snapshot).",()->
                    enqueue(new Job(world,type,new BlockPos(plan.x(),0,plan.z()),force,false,false,source,"confirm",plan)));
                return true;
            }
            place(world,plan);say("Dungeon built: "+plan.describe());return true;
        }
        void say(String text){if(source!=null)source.sendSuccess(()->Component.literal(text),false);}
        void fail(String text){if(source!=null)source.sendFailure(Component.literal(text));}
    }
    static List<String> regionFailures(ServerLevel world,int x,int z,boolean force) {
        var failures=new ArrayList<String>();String dimension=world.dimension().location().toString();
        if(!world.getWorldBorder().isWithinBounds(new BlockPos(x,world.getSeaLevel(),z))||!world.getWorldBorder().isWithinBounds(new BlockPos(x+47,world.getSeaLevel(),z+47)))failures.add("48×48 footprint crosses the world border");
        for(var d:DungeonData.get(world.getServer()).entries.values())if(d.dimension.equals(dimension)) {
            String conflict=dungeonConflict(x,z,d.x,d.z,d.id.toString(),force,config().minimumSpacing);
            if(conflict!=null)failures.add(conflict);
        }
        for(int cx=x>>4;cx<=(x+47)>>4;cx++)for(int cz=z>>4;cz<=(z+47)>>4;cz++)try {
            Class<?> cls=Class.forName("dev.sakhno.regnum.nations.kingdom.DungeonTerritories");
            if(!(boolean)cls.getMethod("unclaimed",ServerLevel.class,int.class,int.class).invoke(null,world,cx,cz))failures.add("chunk ("+cx+", "+cz+") is claimed or Nations data is unavailable");
        }catch(ClassNotFoundException absent){}catch(Exception e){failures.add("Nations claim check failed for chunk ("+cx+", "+cz+"): "+e.getClass().getSimpleName());}
        return failures;
    }
    static String dungeonConflict(int x,int z,int otherX,int otherZ,String id,boolean force,int spacing) {
        if(overlaps(x,z,otherX,otherZ))return "overlaps registered dungeon "+id;
        double distance=Math.hypot((double)otherX-x,(double)otherZ-z);
        return !force&&distance<spacing?"dungeon "+id+" is "+(int)distance+" blocks away; minimum spacing "+spacing:null;
    }
    static boolean overlaps(int x,int z,int otherX,int otherZ){return (long)x<otherX+48L&&(long)x+48>otherX&&(long)z<otherZ+48L&&(long)z+48>otherZ;}
    static Check inspect(ServerLevel world,String type,int x,int z,boolean force,boolean automatic,List<ChunkPos> chunks) {
        var failures=new LinkedHashSet<String>();int[] heights=new int[48*48];int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,top=Integer.MIN_VALUE;
        for(int dx=0;dx<48;dx++)for(int dz=0;dz<48;dz++) {
            int h=ground(world,x+dx,z+dz);heights[dx*48+dz]=h;min=Math.min(min,h);max=Math.max(max,h);
            top=Math.max(top,world.getHeight(Heightmap.Types.WORLD_SURFACE,x+dx,z+dz));
            if(!force&&!world.getFluidState(new BlockPos(x+dx,h-1,z+dz)).isEmpty()&&failures.stream().noneMatch(f->f.startsWith("surface fluid")))failures.add("surface fluid at "+(x+dx)+" "+(h-1)+" "+(z+dz));
        }
        int front=Integer.MIN_VALUE;int back=Integer.MIN_VALUE;
        for(int w=22;w<=26;w++){front=Math.max(front,heights[w*48]);back=Math.max(back,heights[w*48+47]);}
        int[] sorted=heights.clone();Arrays.sort(sorted);int surfaceY=Math.min(sorted[sorted.length/2],Math.min(front,back));
        int y=surfaceY-(type.equals("mine")?10:type.equals("catacombs")?12:0);
        int bottom=y-2;
        // Inspect only foundations that will be touched, rather than rejecting structures far below solid ground.
        for(int dx=0;dx<48;dx++)for(int dz=0;dz<48;dz++) {
            int support=y-2;
            while(support>y-config().placementFoundationDepth&&support>world.getMinBuildHeight()&&!world.getBlockState(new BlockPos(x+dx,support,z+dz)).isSolidRender(world,new BlockPos(x+dx,support,z+dz)))support--;
            bottom=Math.min(bottom,support);
        }
        top=Math.max(top,surfaceY+14);
        if(!force&&max-min>config().placementMaxElevation)failures.add("elevation difference "+(max-min)+", maximum allowed "+config().placementMaxElevation+" (force can adapt larger slopes)");
        if(top-bottom>config().placementMaxVerticalSpan)failures.add("terrain adaptation span "+(top-bottom)+", safety maximum "+config().placementMaxVerticalSpan);
        if(bottom<world.getMinBuildHeight()||top>=world.getMaxBuildHeight())failures.add("terrain writes Y "+bottom+".."+top+" exceed dimension build limits");
        if(!failures.isEmpty())return new Check(null,List.copyOf(failures));
        var bounds=new BoundingBox(x,bottom,z,x+47,top,z+47);
        for(ChunkPos cp:chunks) {
            LevelChunk chunk=world.getChunkSource().getChunkNow(cp.x,cp.z);
            if(chunk==null){failures.add("chunk ("+cp.x+", "+cp.z+") is not loaded");continue;}
            if(automatic&&cp.x>=(x>>4)&&cp.x<=((x+47)>>4)&&cp.z>=(z>>4)&&cp.z<=((z+47)>>4)&&chunk.getInhabitedTime()>0)failures.add("automatic placement avoids previously inhabited chunk ("+cp.x+", "+cp.z+")");
            for(var start:chunk.getAllStarts().values())if(start.isValid()&&start.getBoundingBox().intersects(bounds))failures.add("existing Minecraft structure intersects terrain writes near chunk ("+cp.x+", "+cp.z+")");
        }
        long signature=1;BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();boolean building=false,substrate=false,foundation=false;
        for(int dx=0;dx<48;dx++)for(int dz=0;dz<48;dz++) {
            int surface=heights[dx*48+dz];
            BlockState soil=world.getBlockState(cursor.set(x+dx,surface-1,z+dz));
            if(!force&&!natural(soil)&&!substrate){failures.add("non-natural surface "+BuiltInRegistries.BLOCK.getKey(soil.getBlock())+" at "+cursor.toShortString()+" (force bypasses terrain suitability only)");substrate=true;}
            boolean support=false;
            for(int h=Math.min(surface-1,y-2);h>=bottom;h--)if(world.getBlockState(cursor.set(x+dx,h,z+dz)).isSolidRender(world,cursor)){support=true;break;}
            if(!force&&!support&&!foundation){failures.add("no stable foundation within "+config().placementFoundationDepth+" blocks near "+(x+dx)+", "+(z+dz));foundation=true;}
            boolean wildCanopy=false;
            for(int h=surface;h<=top;h++) {
                BlockState leaf=world.getBlockState(cursor.set(x+dx,h,z+dz));
                if(leaf.getBlock() instanceof LeavesBlock&&!leaf.getValue(LeavesBlock.PERSISTENT)){wildCanopy=true;break;}
            }
            for(int h=bottom;h<=top;h++) {
                BlockState state=world.getBlockState(cursor.set(x+dx,h,z+dz));signature=signature*31+Block.getId(state);
                boolean timber=state.is(BlockTags.LOGS)&&!wildCanopy;
                if((!safeToReplace(state)||timber)&&!building){failures.add("possible player building or block entity: "+BuiltInRegistries.BLOCK.getKey(state.getBlock())+" at "+cursor.toShortString()+"; never bypassed by force");building=true;}
            }
        }
        var occupants=world.getEntities((net.minecraft.world.entity.Entity)null,new AABB(x,bottom,z,x+48,top+1,z+48),e->!(e instanceof ServerPlayer)&&(
            e.hasCustomName()||e instanceof net.minecraft.world.entity.TamableAnimal pet&&pet.isTame()
            ||e instanceof net.minecraft.world.entity.npc.AbstractVillager
            ||!(e instanceof net.minecraft.world.entity.LivingEntity)&&!(e instanceof net.minecraft.world.entity.item.ItemEntity)&&!(e instanceof net.minecraft.world.entity.ExperienceOrb)));
        if(!occupants.isEmpty())failures.add("protected resident/decorative entity "+occupants.getFirst().getType().toShortString()+" at "+occupants.getFirst().blockPosition().toShortString()+"; relocate it before placement");
        return new Check(new Plan(type,x,y,z,front,back,bottom,top,signature),List.copyOf(failures));
    }
    private static int ground(ServerLevel world,int x,int z) {
        int h=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        while(h>world.getMinBuildHeight()+1) {
            BlockState state=world.getBlockState(new BlockPos(x,h-1,z));
            if(!(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||state.getBlock() instanceof BushBlock||state.is(Blocks.SNOW)))break;h--;
        }return h;
    }
    private static boolean natural(BlockState state) {
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.is(BlockTags.DIRT)||state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(BlockTags.BASE_STONE_NETHER)||state.is(BlockTags.SAND)
            ||Set.of("moss_block","rooted_dirt","mud","muddy_mangrove_roots","pointed_dripstone","gravel","clay","snow_block","ice","packed_ice","blue_ice","bedrock","calcite","tuff","dripstone_block","end_stone","obsidian","magma_block","netherrack","soul_sand","soul_soil").contains(id)||id.endsWith("_ore");
    }
    static boolean safeToReplace(BlockState state) {
        if(state.is(Blocks.FARMLAND)||state.is(Blocks.DIRT_PATH)||state.hasBlockEntity()||state.getBlock() instanceof CropBlock||state.getBlock() instanceof LeavesBlock&&state.getValue(LeavesBlock.PERSISTENT))return false;
        return state.isAir()||natural(state)||state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||state.getBlock() instanceof BushBlock
            ||state.is(Blocks.SNOW)||state.is(Blocks.WATER)||state.is(Blocks.LAVA)||state.is(Blocks.MOSS_CARPET)||state.is(Blocks.VINE)||state.is(Blocks.GLOW_LICHEN);
    }
    private static void place(ServerLevel world,Plan plan) {
        // The caller rechecked claims/overlap in this same server tick, with the selected force policy.
        var d=new DungeonData.Dungeon(plan.type(),world.dimension().location().toString(),plan.x(),plan.y(),plan.z());
        d.layoutVersion=3;d.frontY=plan.front();d.backY=plan.back();d.minimumY=Math.min(d.y-8,plan.bottom());d.maximumY=Math.max(d.y+16,plan.top());
        DungeonData.get(world.getServer()).register(d);
        Dungeons.building=true;
        try {
            for(int dx=0;dx<48;dx++)for(int dz=0;dz<48;dz++) {
                for(int h=plan.bottom();h<d.y-1;h++) {
                    BlockPos pos=new BlockPos(d.x+dx,h,d.z+dz);
                    if(!world.getBlockState(pos).isSolidRender(world,pos))world.setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),2);
                }
                if(DungeonStructures.theme(d)==3)for(int h=d.y;h<=plan.top();h++)world.setBlock(new BlockPos(d.x+dx,h,d.z+dz),Blocks.AIR.defaultBlockState(),2);
            }
            DungeonStructures.build(world,d);
            // Operators/players standing in a validated footprint must not be buried in its new foundation or walls.
            for(ServerPlayer player:List.copyOf(world.players()))if(player.getX()>=d.x&&player.getX()<d.x+48&&player.getZ()>=d.z&&player.getZ()<d.z+48&&player.getY()>=plan.bottom()&&player.getY()<=plan.top()+1)
                player.teleportTo(world,d.x+24.5,d.y,d.z+8.5,player.getYRot(),player.getXRot());
        }finally{Dungeons.building=false;}
    }
}
