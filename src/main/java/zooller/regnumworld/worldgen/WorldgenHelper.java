package zooller.regnumworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.config.RegnumConfig;
import zooller.regnumworld.service.RegionalMultiplierService;

public class WorldgenHelper {
    private static final ThreadLocal<Boolean> IS_EXTRA_WORLDGEN = ThreadLocal.withInitial(() -> false);

    public static void handleFeaturePlacement(
            PlacedFeature placedFeature,
            WorldGenLevel level,
            ChunkGenerator generator,
            RandomSource random,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (IS_EXTRA_WORLDGEN.get()) {
            return;
        }

        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return;
        }

        WorldgenTypeResolver.ClassificationResult classification = WorldgenTypeResolver.classify(placedFeature);
        RegnumConfig regnumConfig = RegnumConfig.getInstance();
        double multiplier = 1.0;

        switch (classification.category) {
            case ORE -> multiplier = RegionalMultiplierService.computeOreMultiplier(
                    biomeId,
                    classification.blockId,
                    classification.oreType,
                    regnumConfig
            );
            case TREE -> multiplier = regnumConfig.getTreeMultiplier(biomeId);
            case FLOWER -> multiplier = regnumConfig.getFlowerMultiplier(biomeId);
            case GRASS -> multiplier = regnumConfig.getGrassMultiplier(biomeId);
            case DEAD_BUSH -> multiplier = regnumConfig.getVegetationMultiplier(biomeId, "dead_bush", null);
            default -> {
                return; // Let standard worldgen proceed unaffected
            }
        }

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }

        // Disable feature if multiplier is 0 or negative
        if (multiplier <= 0.0) {
            cir.setReturnValue(false);
            return;
        }

        // Multiplier < 1.0: pure probabilistic filter (skip generation chance)
        if (multiplier < 1.0) {
            if (random.nextDouble() >= multiplier) {
                cir.setReturnValue(false);
            }
            return;
        }

        // Multiplier > 1.0:
        // For non-ores, we do not perform unsafe repeated feature placement to prevent worldgen conflicts.
        // For ores, safely place additional veins strictly inside the chunk boundaries without invoking PlacedFeature.place()
        if (classification.category == WorldgenTypeResolver.FeatureCategory.ORE && regnumConfig.safeExtraOreWorldgenEnabled) {
            double extra = multiplier - 1.0;
            int guaranteedExtra = (int) extra;
            double fractional = extra - guaranteedExtra;
            int extraCount = Math.min(2, guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0));

            if (extraCount > 0) {
                Holder<ConfiguredFeature<?, ?>> featureHolder = placedFeature.feature();
                if (featureHolder != null && featureHolder.isBound()) {
                    ConfiguredFeature<?, ?> configured = featureHolder.value();
                    IS_EXTRA_WORLDGEN.set(true);
                    try {
                        int chunkOriginX = (pos.getX() >> 4) << 4;
                        int chunkOriginZ = (pos.getZ() >> 4) << 4;

                        for (int i = 0; i < extraCount; i++) {
                            // Keep coordinates strictly inside the current 16x16 chunk to avoid chunk border cascading
                            int localX = chunkOriginX + random.nextInt(16);
                            int localZ = chunkOriginZ + random.nextInt(16);
                            int localY = pos.getY() + random.nextInt(9) - 4;

                            BlockPos safeChunkPos = new BlockPos(localX, localY, localZ);
                            configured.place(level, generator, random, safeChunkPos);
                        }
                    } finally {
                        IS_EXTRA_WORLDGEN.set(false);
                    }
                }
            }
        }
    }
}
