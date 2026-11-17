package zooller.regnumworld.dungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Persistent standalone mailbox. Uncertain delivery is quarantined, never automatically retried. */
final class DungeonMailbox {
    private static java.nio.file.Path marker(MinecraftServer server,DungeonData.Dungeon d,CompoundTag claim){
        String key=receipt(d,claim)+":"+claim.getUUID("Player");
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("regnum-reward-deliveries")
            .resolve(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))+".snbt");
    }
    static boolean held(MinecraftServer server,DungeonData.Dungeon d,CompoundTag claim){
        return !retryable(claim)||java.nio.file.Files.exists(marker(server,d,claim));
    }
    /** CREATE_NEW is the durable at-most-once fence; marker payloads remain available for reconciliation. */
    static void reserve(java.nio.file.Path path,String payload) throws java.io.IOException {
        java.nio.file.Files.createDirectories(path.getParent());
        try(var channel=java.nio.channels.FileChannel.open(path,java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)){
            var bytes=java.nio.ByteBuffer.wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            while(bytes.hasRemaining())channel.write(bytes);
            channel.force(true);
        }
        for(var folder:List.of(path.getParent(),path.getParent().getParent()))
            try(var directory=java.nio.channels.FileChannel.open(folder,java.nio.file.StandardOpenOption.READ)){directory.force(true);}
    }
    static boolean retryable(CompoundTag claim){return !claim.getBoolean("DeliveryHeld");}
    static String receipt(DungeonData.Dungeon d,CompoundTag c){
        return d.id+":"+c.getLong("Cycle")+(c.contains("Room")?":room:"+c.getInt("Room"):"");
    }
    static void persist(MinecraftServer server){DungeonData.get(server).setDirty();server.overworld().getDataStorage().save();}
    static List<ItemStack> freeze(MinecraftServer server,CompoundTag claim,List<ItemStack> generated){
        if(!claim.contains("Loot")){
            ListTag list=new ListTag();for(ItemStack stack:generated)if(!stack.isEmpty())list.add(stack.save(server.registryAccess()));
            claim.put("Loot",list);if(!claim.hasUUID("ClaimId"))claim.putUUID("ClaimId",UUID.randomUUID());persist(server);
        }
        List<ItemStack> result=new ArrayList<>();for(Tag raw:claim.getList("Loot",Tag.TAG_COMPOUND))result.add(ItemStack.parseOptional(server.registryAccess(),(CompoundTag)raw));
        return result;
    }
    static List<DungeonData.Dungeon> all(MinecraftServer server){var data=DungeonData.get(server);var all=new ArrayList<>(data.entries.values());all.addAll(data.retired);return all;}
    static LiteralArgumentBuilder<CommandSourceStack> commands(){
        return Commands.literal("rewards").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();for(var d:all(p.getServer()))Dungeons.deliver(p.getServer(),d);
            for(var d:all(p.getServer()))for(var claim:d.pending)if(claim.getUUID("Player").equals(p.getUUID()))
                p.sendSystemMessage(Component.literal((claim.hasUUID("ClaimId")?claim.getUUID("ClaimId").toString():"Preparing")+" · "+(!held(p.getServer(),d,claim)?"pending":"delivery uncertain; administrator review required")));
            return 1;
        }).then(Commands.literal("claim").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
            var p=c.getSource().getPlayerOrException();String id=StringArgumentType.getString(c,"id");
            for(var d:all(p.getServer()))for(var claim:List.copyOf(d.pending))if(claim.getUUID("Player").equals(p.getUUID())&&claim.hasUUID("ClaimId")&&claim.getUUID("ClaimId").toString().equals(id))return claim(p,d,claim);
            c.getSource().sendFailure(Component.literal("Unknown claim. Use /regnum rewards."));return 0;
        })));
    }
    static boolean canFit(ServerPlayer p,List<ItemStack> loot){
        SimpleContainer capacity=new SimpleContainer(36);
        for(int i=0;i<36;i++)capacity.setItem(i,p.getInventory().getItem(i).copy());
        for(ItemStack stack:loot)if(!capacity.addItem(stack.copy()).isEmpty())return false;
        return true;
    }
    private static int claim(ServerPlayer p,DungeonData.Dungeon d,CompoundTag claim){
        // An installed Economy must always use its verification and journal. Never fall back on rejection.
        try {Class.forName("dev.regnum.economy.money.DungeonRewards");Dungeons.deliver(p.getServer(),d);return 1;}
        catch(ClassNotFoundException absent){}
        if(held(p.getServer(),d,claim)||!p.isAlive()||p.isSpectator()||p.hasDisconnected()||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty())return 0;
        List<ItemStack> loot=freeze(p.getServer(),claim,List.of());
        if(!canFit(p,loot)){
            p.sendSystemMessage(Component.literal("Make room for the entire reward, then claim again."));return 0;
        }
        try{var record=claim.copy();record.putUUID("Dungeon",d.id);record.putString("Receipt",receipt(d,claim));reserve(marker(p.getServer(),d,claim),record.toString());}
        catch(java.io.IOException failure){p.sendSystemMessage(Component.literal("Delivery held: storage unavailable or previous delivery requires review."));return 0;}
        claim.putBoolean("DeliveryHeld",true);persist(p.getServer());
        // From here to final persistence, a crash leaves a held claim containing the full payload for review.
        for(ItemStack stack:loot)if(!p.getInventory().add(stack.copy()))throw new IllegalStateException("Mailbox inventory changed during delivery");
        p.getServer().getPlayerList().saveAll();
        d.pending.remove(claim);persist(p.getServer());
        p.sendSystemMessage(Component.literal("Dungeon items received. Coins require Economy."));return 1;
    }
}
