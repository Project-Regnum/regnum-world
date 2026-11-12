package zooller.regnumworld.growth.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SaplingGrowthAdapter implements PlantGrowthAdapter {
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

        if (!(block instanceof SaplingBlock saplingBlock)) {
            return false;
        }

        if (level.getMaxLocalRawBrightness(pos.above()) < 9) {
            return true;
        }

        // Vanilla base chance: 1/7 (≈ 0.143)
        // Multiplier scales this probability up to 1.0 (7x maximum single-tick speedup)
        double targetProbability = Math.min(1.0, multiplier / 7.0);
        if (random.nextDouble() < targetProbability) {
            saplingBlock.advanceTree(level, pos, state, random);
        }

        return true;
    }
}
