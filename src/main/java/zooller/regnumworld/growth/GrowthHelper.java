package zooller.regnumworld.growth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zooller.regnumworld.api.ExternalGrowthController;
import zooller.regnumworld.api.GrowthResult;
import zooller.regnumworld.api.RegnumWorldApi;
import zooller.regnumworld.config.RegnumConfig;
import zooller.regnumworld.growth.adapter.PlantGrowthAdapter;
import zooller.regnumworld.growth.adapter.PlantGrowthAdapters;
import zooller.regnumworld.service.RegionalMultiplierService;

public class GrowthHelper {

    public static void handleRandomTick(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        RegnumConfig config = RegnumConfig.getInstance();

        // 1. Check underground / mine restrictions (Overworld only)
        boolean isUnderground = RegnumWorldApi.isUndergroundGrowthBlocked(level, pos);
        if (isUnderground) {
            ci.cancel();
            return;
        }

        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        double multiplier = RegionalMultiplierService.computeCropMultiplier(biomeId, blockId, pos, isUnderground, config);

        // 2. Negotiate with registered external growth controllers (e.g. Regnum Agriculture)
        for (ExternalGrowthController controller : RegnumWorldApi.getControllers()) {
            GrowthResult result = controller.onRandomTick(state, level, pos, random, multiplier);
            if (result == GrowthResult.HANDLED || result == GrowthResult.CANCEL) {
                ci.cancel();
                return;
            }
        }

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
