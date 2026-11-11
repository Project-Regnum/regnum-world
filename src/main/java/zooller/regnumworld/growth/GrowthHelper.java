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
import zooller.regnumworld.config.RegnumConfig;

public class GrowthHelper {
    private static final ThreadLocal<Boolean> IS_EXTRA_TICK = ThreadLocal.withInitial(() -> false);

    public static void handleRandomTick(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (IS_EXTRA_TICK.get()) {
            return;
        }

        RegnumConfig config = RegnumConfig.getInstance();

        // Check underground / mine restrictions (Overworld only)
        if (config.blockUndergroundCropGrowth) {
            // Below sea level or inside cave without direct sky access
            if (pos.getY() <= config.undergroundMaxY || (config.blockCaveCropGrowthWithoutSky && !level.canSeeSky(pos))) {
                ci.cancel();
                return;
            }
        }

        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        double multiplier = config.getCropMultiplier(biomeId, blockId);

        // Apply mountain altitude debuff if applicable
        if (config.mountainAltitudeDebuffEnabled && pos.getY() >= config.mountainAltitudeStartY) {
            multiplier *= config.mountainAltitudeMultiplier;
        }

        // 1.0 means standard vanilla rate
        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }

        // 0 or negative means no growth
        if (multiplier <= 0.0) {
            ci.cancel();
            return;
        }

        // Less than 1.0: slow down by skipping ticks
        if (multiplier < 1.0) {
            if (random.nextDouble() >= multiplier) {
                ci.cancel();
            }
            return;
        }

        // Greater than 1.0: allow the current tick and execute extra ticks
        double extra = multiplier - 1.0;
        int guaranteedExtra = (int) extra;
        double fractional = extra - guaranteedExtra;

        int extraTicks = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);

        if (extraTicks > 0) {
            IS_EXTRA_TICK.set(true);
            try {
                for (int i = 0; i < extraTicks; i++) {
                    state.randomTick(level, pos, random);
                }
            } finally {
                IS_EXTRA_TICK.set(false);
            }
        }
    }
}
