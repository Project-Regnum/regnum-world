package zooller.regnumworld.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BambooStalkBlock.class)
public interface BambooStalkBlockInvoker {
    @Invoker("growBamboo")
    void invokeGrowBamboo(BlockState state, Level level, BlockPos pos, RandomSource random, int height);

    @Invoker("getHeightAboveUpToMax")
    int invokeGetHeightAboveUpToMax(BlockGetter level, BlockPos pos);
}
