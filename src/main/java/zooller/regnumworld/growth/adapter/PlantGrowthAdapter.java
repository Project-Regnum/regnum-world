package zooller.regnumworld.growth.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public interface PlantGrowthAdapter {
    /**
     * Handles the growth tick for this plant type using a probabilistic multiplier model.
     *
     * @param block The block instance
     * @param state The current block state
     * @param level Server world
     * @param pos Block position
     * @param random Level random source
     * @param multiplier The calculated regional growth multiplier
     * @return true if the vanilla randomTick should be cancelled (either because it was cancelled or custom growth was executed)
     */
    boolean handleGrowth(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, double multiplier);
}
