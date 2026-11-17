package zooller.regnumworld.growth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zooller.regnumworld.api.GrowthResult;
import zooller.regnumworld.api.RegnumWorldApi;
import zooller.regnumworld.growth.adapter.PlantGrowthAdapter;
import zooller.regnumworld.growth.adapter.PlantGrowthAdapters;

public class GrowthHelper {

    public static void handleRandomTick(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if(RegnumWorldApi.isReplayingCropTick())return;
        var policy=RegnumWorldApi.evaluateCropGrowth(level,pos,state);
        if(policy.blocked()){ci.cancel();return;}
        double multiplier=policy.multiplier();
        GrowthResult result=RegnumWorldApi.delegateCropTick(state,level,pos,random,multiplier);
        if(result!=GrowthResult.PASS){ci.cancel();return;}

        // 3. Multiplier <= 0: plant cannot grow
        if (multiplier <= 0.0) {
            ci.cancel();
            return;
        }

        // 4. Dispatch to dedicated plant adapter
        PlantGrowthAdapter adapter = PlantGrowthAdapters.getAdapter(block);
        if (adapter != null) {
            boolean cancelTick = adapter.handleGrowth(block, state, level, pos, random, multiplier);
            if (cancelTick) {
                ci.cancel();
            }
            return;
        }

        // 5. Fallback for generic randomly ticking plants without specific adapter
        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return; // Normal vanilla growth
        }

        if (multiplier < 1.0) {
            if (random.nextDouble() >= multiplier) {
                ci.cancel();
            }
        }
    }
}
