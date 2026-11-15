package zooller.regnumworld.growth.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SugarCaneGrowthAdapter implements PlantGrowthAdapter {
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

        BlockPos abovePos = pos.above();
        if (!level.isEmptyBlock(abovePos)) {
            return true;
        }

        int height = 1;
        while (level.getBlockState(pos.below(height)).is(block)) {
            height++;
        }

        if (height >= 3) {
            return true;
        }

        double extra = multiplier - 1.0;
        int guaranteedExtra = (int) extra;
        double fractional = extra - guaranteedExtra;
        int bonusSteps = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);
        int totalSteps = 1 + bonusSteps;

        int currentAge = state.getValue(SugarCaneBlock.AGE);
        if (currentAge + totalSteps >= 15) {
            level.setBlockAndUpdate(abovePos, block.defaultBlockState());
            level.setBlock(pos, state.setValue(SugarCaneBlock.AGE, 0), 4);
        } else {
            level.setBlock(pos, state.setValue(SugarCaneBlock.AGE, currentAge + totalSteps), 4);
        }

        return true;
    }
}
