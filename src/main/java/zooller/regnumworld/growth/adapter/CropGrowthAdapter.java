package zooller.regnumworld.growth.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import zooller.regnumworld.mixin.CropBlockInvoker;

public class CropGrowthAdapter implements PlantGrowthAdapter {
    @Override
    public boolean handleGrowth(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, double multiplier) {
        if (multiplier <= 0.0) {
            return true;
        }

        if (multiplier < 1.0) {
            if (random.nextDouble() >= multiplier) {
                return true;
            }
            return false; // Let vanilla handle the 1 tick
        }

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return false;
        }

        if (!(block instanceof CropBlock cropBlock)) {
            return false;
        }

        if (level.getRawBrightness(pos, 0) < 9) {
            return true;
        }

        int currentAge = cropBlock.getAge(state);
        int maxAge = cropBlock.getMaxAge();
        if (currentAge >= maxAge) {
            return true;
        }

        float growthSpeed = CropBlockInvoker.invokeGetGrowthSpeed(cropBlock, level, pos);
        int denominator = (int) (25.0F / growthSpeed) + 1;
        double baseChance = 1.0 / denominator;

        double expectedStages = multiplier * baseChance;
        int guaranteedStages = (int) expectedStages;
        double fractional = expectedStages - guaranteedStages;
        int stagesToAdvance = guaranteedStages + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);

        if (stagesToAdvance > 0) {
            int newAge = Math.min(maxAge, currentAge + stagesToAdvance);
            level.setBlock(pos, cropBlock.getStateForAge(newAge), 2);
        }

        return true;
    }
}
