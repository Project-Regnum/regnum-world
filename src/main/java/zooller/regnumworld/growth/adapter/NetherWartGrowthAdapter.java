package zooller.regnumworld.growth.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

public class NetherWartGrowthAdapter implements PlantGrowthAdapter {
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

        int age = state.getValue(NetherWartBlock.AGE);
        if (age >= 3) {
            return true;
        }

        // Vanilla base chance: 1/10 = 0.10
        double expectedStages = multiplier * 0.10;
        int guaranteed = (int) expectedStages;
        double fractional = expectedStages - guaranteed;
        int stages = guaranteed + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);

        if (stages > 0) {
            int newAge = Math.min(3, age + stages);
            level.setBlock(pos, state.setValue(NetherWartBlock.AGE, newAge), 2);
        }

        return true;
    }
}
