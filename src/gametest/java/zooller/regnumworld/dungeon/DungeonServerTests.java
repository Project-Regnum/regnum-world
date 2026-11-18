package zooller.regnumworld.dungeon;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonServerTests implements FabricGameTest {
    private static final String BOSS_AREA = "dungeon.regnum-world.boss_area";
    private static final String BOSS_APPEARED = "dungeon.regnum-world.boss_appeared";

    @GameTest(template=EMPTY_STRUCTURE)
    public void mineBossMessagesFollowSpawnAndReset(GameTestHelper h) {
        bossMessagesFollowSpawnAndReset(h, "mine");
    }

    @GameTest(template=EMPTY_STRUCTURE)
    public void catacombsBossMessagesFollowSpawnAndReset(GameTestHelper h) {
        bossMessagesFollowSpawnAndReset(h, "catacombs");
    }

    @GameTest(template=EMPTY_STRUCTURE)
    public void bastionBossMessagesFollowSpawnAndReset(GameTestHelper h) {
        bossMessagesFollowSpawnAndReset(h, "bastion");
    }

    private void bossMessagesFollowSpawnAndReset(GameTestHelper h, String type) {
        var world = h.getLevel();
        var origin = h.absolutePos(BlockPos.ZERO);
        var d = new DungeonData.Dungeon(type, world.dimension().location().toString(),
                Math.floorDiv(origin.getX(), 16) * 16, origin.getY() + 20, Math.floorDiv(origin.getZ(), 16) * 16);
        var data = DungeonData.get(world.getServer());
        data.register(d);
        var center = DungeonStructures.room(d, 8);
        var trigger = new RecordingPlayer(world, 1, center.offset(-10, 0, 0));
        var teammate = new RecordingPlayer(world, 2, center.offset(-23, 0, 0));
        var outsider = new RecordingPlayer(world, 3, center.offset(12, 0, 0));
        var party = List.<ServerPlayer>of(trigger, teammate);
        trigger.onAppearance = () -> h.assertTrue(d.mobs.keySet().stream().anyMatch(id -> world.getEntity(id) != null),
                "The server must already have accepted the boss at announcement time");

        try {
            fillBossFloor(world, center, true);
            Dungeons.updateBossArea(trigger, d);
            Dungeons.updateBossArea(trigger, d);
            Dungeons.triggerEncounter(world, d, 8, party, 100);
            h.assertTrue(trigger.count(BOSS_AREA) == 1 && trigger.count(BOSS_APPEARED) == 0,
                    "Approaching a locked chamber only announces discovery once");

            unlock(d);
            Dungeons.triggerEncounter(world, d, 8, party, 100);
            h.assertTrue(d.mobs.isEmpty() && !d.started[8] && d.roomReady[8] > 100,
                    "Failed spawn must back off");
            h.assertTrue(trigger.count(BOSS_APPEARED) == 0 && teammate.count(BOSS_APPEARED) == 0,
                    "Blocked boss must never announce its appearance");

            fillBossFloor(world, center, false);
            Dungeons.triggerEncounter(world, d, 8, party, d.roomReady[8]);
            var boss = world.getEntity(d.mobs.keySet().iterator().next());
            h.assertTrue(world.noCollision(boss), "Boss must occupy collision-free space");
            h.assertTrue(trigger.count(BOSS_APPEARED) == 1 && teammate.count(BOSS_APPEARED) == 1
                    && outsider.count(BOSS_APPEARED) == 0, "All participants and only participants see the spawn");

            Dungeons.updateBossArea(trigger, null);
            Dungeons.updateBossArea(trigger, d);
            teammate.setPos(center.getX(), center.getY(), center.getZ() - 10);
            var saved = data.save(new CompoundTag(), world.registryAccess());
            var restored = DungeonData.load(saved, world.registryAccess()).entries.get(d.id);
            Dungeons.triggerEncounter(world, restored, 8, party, d.roomReady[8] + 100);
            h.assertTrue(restored.mobs.size() == 1 && trigger.count(BOSS_AREA) == 2 && trigger.count(BOSS_APPEARED) == 1
                    && teammate.count(BOSS_APPEARED) == 1, "Reentry and restored live bosses must not replay appearance");

            var commands = world.getServer().getCommands();
            var source = trigger.createCommandSourceStack().withPermission(4);
            commands.performPrefixedCommand(source, "regnum dungeon reset " + d.id);
            var confirmation = trigger.messages.getLast().getSiblings().stream().map(c -> c.getStyle().getClickEvent())
                    .filter(event -> event != null && event.getValue().startsWith("/regnum dungeon confirm ")).findFirst().orElseThrow();
            commands.performPrefixedCommand(source, confirmation.getValue());
            h.assertTrue(d.cycle == 1 && d.mobs.isEmpty() && !d.cleared[4], "Admin reset clears the encounter");
            unlock(d);
            Dungeons.triggerEncounter(world, d, 8, party, 1000);
            h.assertTrue(trigger.count(BOSS_APPEARED) == 2 && teammate.count(BOSS_APPEARED) == 2,
                    "A newly accepted boss after reset announces once");

            world.getEntity(d.mobs.keySet().iterator().next()).discard();
            d.mobs.clear();
            DungeonEncounters.missing(d, 8, 1000, 500);
            h.assertTrue(!d.bossDefeated && !d.cleared[8] && d.pending.isEmpty(), "Discard must never grant victory");
            saved = data.save(new CompoundTag(), world.registryAccess());
            restored = DungeonData.load(saved, world.registryAccess()).entries.get(d.id);
            h.assertTrue(restored.bossReady == 1500 && !restored.bossDefeated, "Recovery cooldown must survive persistence");
        } finally {
            Dungeons.updateBossArea(trigger, null);
            Dungeons.clear(world, d);
            data.remove(d);
        }
        h.succeed();
    }

    private static void unlock(DungeonData.Dungeon dungeon) {
        java.util.Arrays.fill(dungeon.cleared, 0, 8, true);
    }

    private static void fillBossFloor(ServerLevel world, BlockPos center, boolean blocked) {
        Dungeons.building = true;
        try {
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) for (int y = -3; y <= 5; y++) {
                world.setBlock(center.offset(x, y, z), (blocked || y < 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
        } finally {
            Dungeons.building = false;
        }
    }

    private static final class RecordingPlayer extends ServerPlayer {
        private final List<Component> messages = new ArrayList<>();
        private Runnable onAppearance = () -> {};

        private RecordingPlayer(ServerLevel level, int id, BlockPos position) {
            super(level.getServer(), level, new GameProfile(new UUID(0, id), "DungeonTester" + id), ClientInformation.createDefault());
            setPos(position.getX(), position.getY(), position.getZ());
        }

        @Override public void sendSystemMessage(Component message) {
            messages.add(message);
            if (hasKey(message, BOSS_APPEARED)) onAppearance.run();
        }

        private long count(String key) {
            return messages.stream().filter(message -> hasKey(message, key)).count();
        }

        private static boolean hasKey(Component message, String key) {
            return message.getContents() instanceof TranslatableContents text && text.getKey().equals(key);
        }
    }

    @GameTest(template=EMPTY_STRUCTURE)
    public void atlasUsesOnlyRegisteredDungeonsWithoutLoadingChunks(GameTestHelper h) {
        var world = h.getLevel();
        var server = world.getServer();
        var data = DungeonData.get(server);
        int x = -4096, y = 64, z = -4096;
        var mine = new DungeonData.Dungeon("mine", world.dimension().location().toString(), x, y, z);
        var catacombs = new DungeonData.Dungeon("catacombs", mine.dimension, x + 48, y, z);
        var bastion = new DungeonData.Dungeon("bastion", "minecraft:the_nether", x, y, z);
        mine.frontY = 73;
        var registered = List.of(mine, catacombs, bastion);
        for (var dungeon : registered) data.register(dungeon);
        try {
            assertMarkers(h, registered);
            var saved = data.save(new CompoundTag(), world.registryAccess());
            saved.getList("Dungeons", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("LayoutVersion");
            var restored = DungeonData.load(saved, world.registryAccess());
            for (var dungeon : registered) {
                h.assertTrue(restored.entries.get(dungeon.id).atlasMarker().equals(dungeon.atlasMarker()),
                        "Saving and reloading must preserve atlas placement");
            }
            data.remove(catacombs);
            data.retired.add(catacombs);
            assertMarkers(h, List.of(mine, bastion));
            h.assertTrue(Dungeons.atlasSnapshot(server).stream().noneMatch(tag -> ((CompoundTag)tag).getUUID("Id").equals(catacombs.id)),
                    "Unregistered dungeons stay absent even when rewards are retained");
            h.assertFalse(world.getChunkSource().hasChunk(x >> 4, z >> 4), "Atlas reads must not load remote chunks");
        } finally {
            data.remove(mine);
            data.remove(bastion);
            data.retired.remove(catacombs);
        }
        h.succeed();
    }

    private static void assertMarkers(GameTestHelper h, List<DungeonData.Dungeon> expected) {
        var snapshot = Dungeons.atlasSnapshot(h.getLevel().getServer());
        for (var dungeon : expected) {
            var marker = snapshot.stream().map(CompoundTag.class::cast).filter(tag -> tag.getUUID("Id").equals(dungeon.id))
                    .findFirst().orElseThrow();
            h.assertTrue(marker.getString("Type").equals(dungeon.type) && marker.getString("Dimension").equals(dungeon.dimension)
                    && java.util.Arrays.equals(marker.getIntArray("Entrances"),
                            new int[]{dungeon.x + 24, dungeon.frontY, dungeon.z, dungeon.x + 24, dungeon.backY, dungeon.z + 47}),
                    "Each theme must have its registered dimension and entrance coordinates");
            h.assertFalse(marker.contains("Pending"), "Atlas data must omit reward data");
        }
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
