package zooller.regnumworld.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.dungeon.Dungeons;

/** Protect terrain writes from explosions, fluids, fire, mobs and indirect player actions. */
@Mixin(Level.class)
public abstract class DungeonLevelMixin {
    @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
    private void regnum$dungeonDestruction(BlockPos pos,boolean drops,net.minecraft.world.entity.Entity actor,int recursion,CallbackInfoReturnable<Boolean> cir) {
        if(!Dungeons.building && !Dungeons.adminWriting.get() && Dungeons.protectedAt((Level)(Object)this,pos))cir.setReturnValue(false);
    }

    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void regnum$dungeonTerrain(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> cir) {
        Level world=(Level)(Object)this;
        BlockState old=world.getBlockState(pos);
        boolean mechanism=old.getBlock()==state.getBlock()&&(old.getBlock() instanceof net.minecraft.world.level.block.DoorBlock||old.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock||old.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock||old.getBlock() instanceof net.minecraft.world.level.block.LeverBlock||old.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock);
        if(mechanism) {
            for(var property:old.getProperties())if(!property.getName().equals("open")&&!property.getName().equals("powered")&&!old.getValue(property).equals(state.getValue(property)))mechanism=false;
        }
        if(!Dungeons.building&&!Dungeons.adminWriting.get()&&!mechanism&&Dungeons.protectedAt(world,pos))cir.setReturnValue(false);
    }
}
