package zooller.regnumworld.growth.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import zooller.regnumworld.mixin.BambooStalkBlockInvoker;

public class BambooGrowthAdapter implements PlantGrowthAdapter {
    @Override
    public boolean handleGrowth(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, double multiplier) {
        if (multiplier <= 0.0) {
            return true;
        }

        if (multiplier < 1.0) {
            if (random.nextDouble() >= multiplier) {
                return true;
            }
            return false;
        }

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return false;
        }

        if (!(block instanceof BambooStalkBlock bambooBlock)) {
            return false;
        }

        if (state.getValue(BambooStalkBlock.STAGE) != 0) {
            return true;
        }

        // Vanilla base probability is 1/3 (≈ 0.333)
        // Multiplier scales this probability up to 1.0 (3x maximum single-tick speedup)
        double targetProbability = Math.min(1.0, multiplier / 3.0);
        if (random.nextDouble() < targetProbability) {
            BlockPos abovePos = pos.above();
            if (level.isEmptyBlock(abovePos) && level.getRawBrightness(abovePos, 0) >= 9) {
                BambooStalkBlockInvoker invoker = (BambooStalkBlockInvoker) bambooBlock;
                int height = invoker.invokeGetHeightAboveUpToMax(level, pos) + 1;
                if (height < 16) {
                    invoker.invokeGrowBamboo(state, level, pos, random, height);
                }
            }
        }

        return true;
    }
}
