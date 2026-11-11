package zooller.regnumworld.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zooller.regnumworld.growth.GrowthHelper;

@Mixin(CactusBlock.class)
public class CactusBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void regnumworld$onCactusRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        GrowthHelper.handleRandomTick((Block)(Object)this, state, level, pos, random, ci);
    }
}
