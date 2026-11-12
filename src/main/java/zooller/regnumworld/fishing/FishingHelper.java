package zooller.regnumworld.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.biome.Biome;
import zooller.regnumworld.config.RegnumConfig;
import zooller.regnumworld.service.RegionalMultiplierService;

public class FishingHelper {

    public static double getMultiplier(FishingHook hook) {
        BlockPos pos = hook.blockPosition();
        Holder<Biome> biomeHolder = hook.level().getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return 1.0;
        }
        return RegionalMultiplierService.computeFishingMultiplier(biomeId, RegnumConfig.getInstance());
    }

    /**
     * Prevents fish biting when multiplier <= 0.
     * Called at tick HEAD.
     */
    public static void handleTickHead(FishingHook hook, int timeUntilLured, int timeUntilHooked) {
        double multiplier = getMultiplier(hook);
        if (multiplier <= 0.0) {
            // Keep timer comfortably above zero so vanilla catchingFish() never transitions to hooked
            if (timeUntilLured <= 5) {
                // Keep wait time high
                setLuredTimer(hook, 300);
            }
        }
    }

    /**
     * Accurately scales wait time countdown.
     * Called at tick TAIL when timeUntilLured > 0.
     */
    public static int adjustWaitTime(FishingHook hook, int currentLured, RandomSource random) {
        if (currentLured <= 0) {
            return currentLured;
        }

        double multiplier = getMultiplier(hook);

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return currentLured;
        }

        // Multiplier <= 0: fish never bite
        if (multiplier <= 0.0) {
            return Math.max(300, currentLured + 1); // negate vanilla's decrement
        }

        // Slower fishing (0 < multiplier < 1.0)
        // With probability (1.0 - multiplier), counteract the 1 tick that vanilla just decremented
        if (multiplier < 1.0) {
            if (random.nextDouble() < (1.0 - multiplier)) {
                return currentLured + 1; // negate the tick
            }
            return currentLured;
        }

        // Faster fishing (multiplier > 1.0)
        // Average decrement is 1 + extra = multiplier per tick
        double extra = multiplier - 1.0;
        int guaranteedExtra = (int) extra;
        double fractional = extra - guaranteedExtra;
        int extraDecrement = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);

        // Clamp to at least 1 so vanilla can trigger the hook transition on the subsequent tick
        return Math.max(1, currentLured - extraDecrement);
    }

    private static void setLuredTimer(FishingHook hook, int timer) {
        // Will be shadowed in mixin
    }
}
