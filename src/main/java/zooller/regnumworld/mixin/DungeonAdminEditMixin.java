package zooller.regnumworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.*;
import zooller.regnumworld.dungeon.Dungeons;

@Mixin(ServerPlayerGameMode.class)
public abstract class DungeonAdminEditMixin {
    @Shadow @Final protected ServerPlayer player;
    @WrapMethod(method="destroyBlock")
    private boolean regnum$adminBreak(BlockPos pos,Operation<Boolean> original) {
        boolean previous=Dungeons.adminWriting.get();Dungeons.adminWriting.set(Dungeons.admin(player));
        try{return original.call(pos);}finally{Dungeons.adminWriting.set(previous);}
    }
    @WrapMethod(method="useItemOn")
    private InteractionResult regnum$adminPlace(ServerPlayer actor,Level level,ItemStack stack,InteractionHand hand,BlockHitResult hit,Operation<InteractionResult> original) {
        boolean previous=Dungeons.adminWriting.get();Dungeons.adminWriting.set(Dungeons.admin(actor));
        try{return original.call(actor,level,stack,hand,hit);}finally{Dungeons.adminWriting.set(previous);}
    }
}
