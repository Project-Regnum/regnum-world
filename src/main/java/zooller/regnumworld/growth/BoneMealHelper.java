package zooller.regnumworld.growth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.config.RegnumConfig;

public class BoneMealHelper {
    public static void handleBonemeal(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        RegnumConfig config = RegnumConfig.getInstance();

        // 1. Block bone meal in underground mines / dark caves
        if (config.blockBonemealUnderground && config.blockUndergroundCropGrowth) {
            boolean isUnderground = pos.getY() <= config.undergroundMaxY ||
                    (config.blockCaveCropGrowthWithoutSky && !level.canSeeSky(pos));
            if (isUnderground) {
                cir.setReturnValue(false);
                return;
            }
        }

        // 2. Global ~50% nerf + regional adjustment
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);

        double efficiency = config.bonemealEfficiency; // default 0.5 (50% reduction)

        if (biomeId != null) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            double cropMult = config.getCropMultiplier(biomeId, blockId);

            // If crop completely cannot grow in this biome, bone meal also cannot force it
            if (cropMult <= 0.0) {
                cir.setReturnValue(false);
                return;
            }

            // In debuffed regions (e.g. desert / snow), bone meal is even weaker
            if (cropMult < 1.0) {
                efficiency *= cropMult;
            }
        }

        // Random roll for efficiency check
        if (level.random.nextDouble() >= efficiency) {
            cir.setReturnValue(false);
        }
    }
}
