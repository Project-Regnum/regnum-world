package zooller.regnumworld.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.level.biome.Biome;
import zooller.regnumworld.config.RegnumConfig;

public class AnimalGrowthHelper {
    public static void handleAnimalTick(AgeableMob mob) {
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

        double multiplier = RegnumConfig.getInstance().getAnimalGrowthMultiplier(biomeId);
        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }

        RandomSource random = mob.level().getRandom();

        // 1. Baby growing up (age is negative, counting up to 0)
        if (age < 0) {
            if (multiplier > 1.0) {
                // Accelerate maturation
                double extra = multiplier - 1.0;
                int guaranteedExtra = (int) extra;
                double fractional = extra - guaranteedExtra;

                int extraTicks = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);
                if (extraTicks > 0) {
                    mob.setAge(Math.min(0, age + extraTicks));
                }
            } else if (multiplier < 1.0) {
                // Slow down maturation (counteract tick with chance 1.0 - multiplier)
                if (random.nextDouble() < (1.0 - multiplier)) {
                    mob.setAge(age - 1); // negate vanilla's +1
                }
            }
        }
        // 2. Breeding cooldown (age is positive, counting down to 0)
        else {
            if (multiplier > 1.0) {
                // Shorter breeding cooldown in fertile pastures
                double extra = multiplier - 1.0;
                int guaranteedExtra = (int) extra;
                double fractional = extra - guaranteedExtra;

                int extraTicks = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);
                if (extraTicks > 0) {
                    mob.setAge(Math.max(0, age - extraTicks));
                }
            } else if (multiplier < 1.0) {
                // Longer breeding cooldown in harsh environments
                if (random.nextDouble() < (1.0 - multiplier)) {
                    mob.setAge(age + 1); // negate vanilla's -1
                }
            }
        }
    }
}
