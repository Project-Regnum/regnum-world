package zooller.regnumworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
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
        if (IS_EXTRA_WORLDGEN.get() || placedFeature == null || level == null ||
                generator == null || random == null || pos == null || cir == null ||
                level.isOutsideBuildHeight(pos)) {
            return;
        }

        WorldgenTypeResolver.ClassificationResult classification = WorldgenTypeResolver.classify(placedFeature, pos);
        if (classification.category == WorldgenTypeResolver.FeatureCategory.OTHER) {
            return;
        }

        // Use only biomes already available to this generation region. getBiome() can
        // request neighboring chunks through biome smoothing, including unavailable ones.
        ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.BIOMES, false);
        if (chunk == null) {
            return;
        }
        Holder<Biome> biomeHolder = chunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        if (biomeHolder == null || !biomeHolder.isBound()) {
            return;
        }
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return;
        }

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
        // Extra ores require a known vanilla footprint, including reads of adjacent blocks.
        if (classification.category == WorldgenTypeResolver.FeatureCategory.ORE && regnumConfig.safeExtraOreWorldgenEnabled) {
            double extra = multiplier - 1.0;
            int guaranteedExtra = (int) extra;
            double fractional = extra - guaranteedExtra;
            int extraCount = Math.min(2, guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0));

            if (extraCount > 0) {
                Holder<ConfiguredFeature<?, ?>> featureHolder = placedFeature.feature();
                if (featureHolder != null && featureHolder.isBound()) {
                    ConfiguredFeature<?, ?> configured = featureHolder.value();
                    int margin = extraOreMargin(configured);
                    // Include heightmap probes and air-exposure reads, not just the origin.
                    if (margin < 0 || margin > 7 ||
                            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.CARVERS, false) == null) {
                        return;
                    }
                    IS_EXTRA_WORLDGEN.set(true);
                    try {
                        int chunkOriginX = (pos.getX() >> 4) << 4;
                        int chunkOriginZ = (pos.getZ() >> 4) << 4;

                        for (int i = 0; i < extraCount; i++) {
                            // Keep coordinates strictly inside the current 16x16 chunk to avoid chunk border cascading
                            int localX = chunkOriginX + margin + random.nextInt(16 - 2 * margin);
                            int localZ = chunkOriginZ + margin + random.nextInt(16 - 2 * margin);
                            int localY = pos.getY() + random.nextInt(9) - 4;

                            BlockPos safeChunkPos = new BlockPos(localX, localY, localZ);
                            if (localY - margin < level.getMinBuildHeight() ||
                                    localY + margin >= level.getMaxBuildHeight() ||
                                    !level.ensureCanWrite(safeChunkPos)) {
                                continue;
                            }
                            configured.place(level, generator, random, safeChunkPos);
                        }
                    } finally {
                        IS_EXTRA_WORLDGEN.remove();
                    }
                }
            }
        }
    }

    /** Conservative vanilla bounds; unknown modded features must not be replayed. */
    private static int extraOreMargin(ConfiguredFeature<?, ?> configured) {
        if (!(configured.config() instanceof OreConfiguration ore) || ore.size < 0 || ore.size > 64) {
            return -1;
        }
        if (configured.feature() == Feature.ORE) {
            int radius = (int) Math.ceil(ore.size / 16.0 + 0.5);
            return Math.max((int) Math.ceil(ore.size / 8.0) + radius, 2 + radius) + 1;
        }
        if (configured.feature() == Feature.SCATTERED_ORE) {
            return Math.min(ore.size, 7) + 1;
        }
        return -1;
    }
}
