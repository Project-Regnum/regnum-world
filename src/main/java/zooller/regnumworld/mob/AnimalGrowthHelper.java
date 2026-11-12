package zooller.regnumworld.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.level.biome.Biome;
import zooller.regnumworld.config.RegnumConfig;
import zooller.regnumworld.service.RegionalMultiplierService;

public class AnimalGrowthHelper {
    private static final int CHECK_INTERVAL = 20; // Check once per second (20 ticks) for high performance

    public static void handleAnimalTick(AgeableMob mob) {
        // Only run on throttled interval to avoid firing network sync packets every tick
        if (mob.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        int age = mob.getAge();
        if (age == 0) {
            return;
        }

        BlockPos pos = mob.blockPosition();
        Holder<Biome> biomeHolder = mob.level().getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return;
        }

        double multiplier = RegionalMultiplierService.computeAnimalGrowthMultiplier(
                biomeId,
                RegnumConfig.getInstance()
        );

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }

        // Over CHECK_INTERVAL ticks, vanilla has adjusted age by CHECK_INTERVAL.
        // Desired total progress = CHECK_INTERVAL * multiplier.
        // Difference to apply:
        int delta = (int) Math.round(CHECK_INTERVAL * (multiplier - 1.0));

        // 1. Baby growing up (age < 0, counting up to 0)
        if (age < 0) {
            if (multiplier <= 0.0) {
                // Completely freeze baby growth (counteract the ticks vanilla added)
                mob.setAge(age - CHECK_INTERVAL);
                return;
            }

            if (delta != 0) {
                int newAge = Math.min(0, age + delta);
                mob.setAge(newAge);
            }
        }
        // 2. Breeding cooldown (age > 0, counting down to 0)
        else {
            if (multiplier <= 0.0) {
                // Cooldown never expires (counteract the ticks vanilla decremented)
                mob.setAge(age + CHECK_INTERVAL);
                return;
            }

            if (delta != 0) {
                int newAge = Math.max(0, age - delta);
                mob.setAge(newAge);
            }
        }
    }
}
