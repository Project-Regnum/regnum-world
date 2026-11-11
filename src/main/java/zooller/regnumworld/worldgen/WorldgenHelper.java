package zooller.regnumworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.config.RegnumConfig;

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

        Holder<ConfiguredFeature<?, ?>> featureHolder = placedFeature.feature();
        if (featureHolder == null || !featureHolder.isBound()) {
            return;
        }

        ConfiguredFeature<?, ?> configuredFeature = featureHolder.value();
        Object config = configuredFeature.config();
        String featurePath = featureHolder.unwrapKey().map(k -> k.location().getPath()).orElse("");

        boolean isTree = config instanceof TreeConfiguration || featurePath.contains("tree");
        boolean isOre = config instanceof OreConfiguration || featurePath.contains("ore");
        boolean isFlower = featurePath.contains("flower") || featurePath.contains("sunflower") ||
                featurePath.contains("rose_bush") || featurePath.contains("lilac") || featurePath.contains("peony");
        boolean isGrass = featurePath.contains("grass") || featurePath.contains("fern");
        boolean isDeadBush = featurePath.contains("dead_bush");

        // Inspect RandomPatchConfiguration for flowers/grass/bushes
        if (config instanceof RandomPatchConfiguration patch) {
            Holder<PlacedFeature> innerHolder = patch.feature();
            if (innerHolder != null && innerHolder.isBound()) {
                String innerPath = innerHolder.unwrapKey().map(k -> k.location().getPath()).orElse("");
                if (innerPath.contains("flower") || innerPath.contains("sunflower") || innerPath.contains("rose")) {
                    isFlower = true;
                } else if (innerPath.contains("grass") || innerPath.contains("fern")) {
                    isGrass = true;
                } else if (innerPath.contains("dead_bush")) {
                    isDeadBush = true;
                }
            }
        }

        String oreCategory = null;
        ResourceLocation oreBlockId = null;

        if (isOre) {
            if (config instanceof OreConfiguration oreConfig) {
                for (OreConfiguration.TargetBlockState target : oreConfig.targetStates) {
                    Block targetBlock = target.state.getBlock();
                    oreBlockId = BuiltInRegistries.BLOCK.getKey(targetBlock);
                    String name = oreBlockId.getPath();
                    if (name.contains("coal")) oreCategory = "coal";
                    else if (name.contains("iron")) oreCategory = "iron";
                    else if (name.contains("copper")) oreCategory = "copper";
                    else if (name.contains("gold")) oreCategory = "gold";
                    else if (name.contains("redstone")) oreCategory = "redstone";
                    else if (name.contains("diamond")) oreCategory = "diamond";
                    else if (name.contains("lapis")) oreCategory = "lapis";
                    else if (name.contains("emerald")) oreCategory = "emerald";
                    else if (name.contains("quartz")) oreCategory = "quartz";
                    else if (name.contains("debris")) oreCategory = "debris";

                    if (oreCategory != null) {
                        break;
                    }
                }
            }

            if (oreCategory == null) {
                if (featurePath.contains("coal")) oreCategory = "coal";
                else if (featurePath.contains("iron")) oreCategory = "iron";
                else if (featurePath.contains("copper")) oreCategory = "copper";
                else if (featurePath.contains("gold")) oreCategory = "gold";
                else if (featurePath.contains("redstone")) oreCategory = "redstone";
                else if (featurePath.contains("diamond")) oreCategory = "diamond";
                else if (featurePath.contains("lapis")) oreCategory = "lapis";
                else if (featurePath.contains("emerald")) oreCategory = "emerald";
                else if (featurePath.contains("quartz")) oreCategory = "quartz";
                else if (featurePath.contains("debris")) oreCategory = "debris";
            }
        }

        RegnumConfig regnumConfig = RegnumConfig.getInstance();
        double multiplier = 1.0;

        if (isOre) {
            multiplier = regnumConfig.getOreMultiplier(biomeId, oreCategory, oreBlockId);
        } else if (isTree) {
            multiplier = regnumConfig.getTreeMultiplier(biomeId);
        } else if (isFlower) {
            multiplier = regnumConfig.getFlowerMultiplier(biomeId);
        } else if (isGrass) {
            multiplier = regnumConfig.getGrassMultiplier(biomeId);
        } else if (isDeadBush) {
            multiplier = regnumConfig.getVegetationMultiplier(biomeId, "dead_bush", null);
        } else {
            // Check specific vegetation or feature path
            multiplier = regnumConfig.getVegetationMultiplier(biomeId, featurePath, null);
        }

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }

        // Completely disable feature if multiplier is 0 or less
        if (multiplier <= 0.0) {
            cir.setReturnValue(false);
            return;
        }

        // Reduce frequency
        if (multiplier < 1.0) {
            if (random.nextDouble() >= multiplier) {
                cir.setReturnValue(false);
            }
            return;
        }

        // Increase frequency (> 1.0)
        double extra = multiplier - 1.0;
        int guaranteedExtra = (int) extra;
        double fractional = extra - guaranteedExtra;

        int extraCount = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);

        if (extraCount > 0) {
            IS_EXTRA_WORLDGEN.set(true);
            try {
                for (int i = 0; i < extraCount; i++) {
                    int dx = random.nextInt(15) - 7;
                    int dz = random.nextInt(15) - 7;
                    int dy = random.nextInt(5) - 2;
                    BlockPos newPos = pos.offset(dx, dy, dz);
                    placedFeature.place(level, generator, random, newPos);
                }
            } finally {
                IS_EXTRA_WORLDGEN.set(false);
            }
        }
    }
}
