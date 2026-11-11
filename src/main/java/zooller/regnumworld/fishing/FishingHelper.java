package zooller.regnumworld.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.biome.Biome;
import zooller.regnumworld.config.RegnumConfig;

public class FishingHelper {
    public static int adjustWaitTime(FishingHook hook, int currentLured, RandomSource random) {
        if (currentLured <= 0) {
            return currentLured;
        }

        BlockPos pos = hook.blockPosition();
        Holder<Biome> biomeHolder = hook.level().getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return currentLured;
        }

        double multiplier = RegnumConfig.getInstance().getFishingMultiplier(biomeId);

        // Standard vanilla speed
        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return currentLured;
        }

        // Completely disabled (fish never bite)
        if (multiplier <= 0.0) {
            return currentLured + 1; // negate the tick
        }

        // Slower fishing (e.g. desert, snow, frozen water)
        if (multiplier < 1.0) {
            // Cancel tick decrement with probability (1.0 - multiplier)
            if (random.nextDouble() < (1.0 - multiplier)) {
                return currentLured + 1; // negate tick
            }
            return currentLured;
        }

        // Faster fishing (e.g. river, ocean, warm ocean, swamp)
        double extra = multiplier - 1.0;
        int guaranteedExtra = (int) extra;
        double fractional = extra - guaranteedExtra;

        int extraTicks = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);
        return Math.max(1, currentLured - extraTicks);
    }
}
