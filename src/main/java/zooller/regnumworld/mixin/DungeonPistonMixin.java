package zooller.regnumworld.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.dungeon.Dungeons;

@Mixin(PistonBaseBlock.class)
public abstract class DungeonPistonMixin {
    @Inject(method="triggerEvent",at=@At("HEAD"),cancellable=true)
    private void regnum$dungeonMovement(BlockState state,Level world,BlockPos pos,int type,int data,CallbackInfoReturnable<Boolean> cir) {
        if(world.isClientSide||Dungeons.config==null||!Dungeons.config.protection)return;
        var facing=state.getValue(PistonBaseBlock.FACING);
        if(Dungeons.protectedAt(world,pos)||Dungeons.protectedAt(world,pos.relative(facing))||Dungeons.protectedAt(world,pos.relative(facing,2))){cir.setReturnValue(false);return;}
        var graph=new PistonStructureResolver(world,pos,facing,type==0);
        if(!graph.resolve())return;
        var travel=type==0?facing:facing.getOpposite();
        for(BlockPos source:graph.getToPush())if(Dungeons.protectedAt(world,source)||Dungeons.protectedAt(world,source.relative(travel))){cir.setReturnValue(false);return;}
        for(BlockPos source:graph.getToDestroy())if(Dungeons.protectedAt(world,source)){cir.setReturnValue(false);return;}
    }
}
