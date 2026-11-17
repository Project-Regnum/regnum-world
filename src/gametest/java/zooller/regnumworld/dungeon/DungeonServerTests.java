package zooller.regnumworld.dungeon;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public final class DungeonServerTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE)
    public void blockedSpawnRecoversAndBossAbsenceIsNotVictory(GameTestHelper h) throws Exception {
        var world=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);
        var d=new DungeonData.Dungeon("mine",world.dimension().location().toString(),Math.floorDiv(origin.getX(),16)*16,origin.getY()+20,Math.floorDiv(origin.getZ(),16)*16);
        var data=DungeonData.get(world.getServer());data.register(d);
        Dungeons.building=true;
        try {
            var center=DungeonStructures.room(d,8);
            for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=-3;y<=5;y++)world.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
            Dungeons.spawn(world,d,8,center.getX(),center.getZ(),true);
            DungeonEncounters.spawned(d,8,100,500);
            h.assertTrue(d.mobs.isEmpty()&&!d.started[8]&&d.roomReady[8]==600,"Failed spawn must back off");
            for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=0;y<=5;y++)world.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            Dungeons.spawn(world,d,8,center.getX(),center.getZ(),true);
            Dungeons.spawn(world,d,8,center.getX(),center.getZ(),true);
            h.assertTrue(d.mobs.size()==1,"Clear floor must spawn exactly one boss even on repeated requests");
            Entity boss=world.getEntity(d.mobs.keySet().iterator().next());
            h.assertTrue(boss!=null&&world.noCollision(boss),"Boss must occupy collision-free space");
            boss.discard();d.mobs.clear();DungeonEncounters.missing(d,8,700,500);
            h.assertTrue(!d.bossDefeated&&!d.cleared[8]&&d.pending.isEmpty(),"Discard must never grant victory");
            var snapshot=data.save(new net.minecraft.nbt.CompoundTag(),world.registryAccess());
            var restored=DungeonData.load(snapshot,world.registryAccess()).entries.get(d.id);
            h.assertTrue(restored.bossReady==1200&&!restored.bossDefeated,"Recovery cooldown must survive persistence");
        }finally{Dungeons.building=false;Dungeons.clear(world,d);data.remove(d);}
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE)
    public void lootSnapshotAndOptionalEconomyReceiptsAreStable(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer();var claim=new net.minecraft.nbt.CompoundTag();
        claim.putUUID("Player",java.util.UUID.randomUUID());
        var loot=java.util.List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,2));
        DungeonMailbox.freeze(server,claim,loot);
        var retried=DungeonMailbox.freeze(server,claim,java.util.List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT,64)));
        h.assertTrue(retried.size()==1&&retried.getFirst().is(net.minecraft.world.item.Items.DIAMOND)&&retried.getFirst().getCount()==2,"Actual loot must be frozen before delivery");
        try {
            var api=Class.forName("dev.regnum.economy.money.DungeonRewards");
            var p=h.makeMockServerPlayerInLevel();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false));
            var grant=api.getMethod("grant",net.minecraft.server.level.ServerPlayer.class,String.class,long.class,java.util.List.class);
            String receipt="world-test:"+java.util.UUID.randomUUID();
            h.assertTrue((boolean)grant.invoke(null,p,receipt,10L,retried),"Economy must accept a valid journalled reward");
            h.assertTrue((boolean)grant.invoke(null,p,receipt,10L,retried)&&p.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND)==2,"Receipt retry must not duplicate items");
            for(int i=0;i<36;i++)p.getInventory().setItem(i,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
            h.assertTrue(!DungeonMailbox.canFit(p,retried),"World capacity check must reject a full inventory");
            h.assertTrue(!(boolean)grant.invoke(null,p,receipt+":full",10L,retried),"Full inventory must keep reward pending");
        }catch(ClassNotFoundException standalone){}
        h.succeed();
    }
}
