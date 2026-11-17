package zooller.regnumworld.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface ExternalGrowthController {
    /**
     * Invoked on the server thread after World's underground restriction check.
     * Apply regionalMultiplier once. Return PASS only without mutating growth.
     * For original vanilla ticks use RegnumWorldApi.replayCropTick inside this
     * synchronous callback, then return HANDLED; never intercept GrowthHelper.
     * The first non-PASS result wins in registration order. No seasons are supplied.
     *
     * @param state Current block state
     * @param level Server level
     * @param pos Block position
     * @param random Random source
     * @param regionalMultiplier The regional multiplier computed by Regnum World (biome, altitude, underground)
     * @return GrowthResult indicating whether the external mod handled, cancelled, or passed the tick.
     */
    GrowthResult onRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, double regionalMultiplier);
}
