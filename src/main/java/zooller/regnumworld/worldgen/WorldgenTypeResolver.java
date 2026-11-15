package zooller.regnumworld.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class WorldgenTypeResolver {
    public enum FeatureCategory {
        ORE,
        TREE,
        FLOWER,
        GRASS,
        DEAD_BUSH,
        OTHER
    }

    public static class ClassificationResult {
        public final FeatureCategory category;
        public final String oreType;
        public final ResourceLocation blockId;

        public ClassificationResult(FeatureCategory category, String oreType, ResourceLocation blockId) {
            this.category = category;
            this.oreType = oreType;
            this.blockId = blockId;
        }
    }

    private WorldgenTypeResolver() {}

    public static ClassificationResult classify(PlacedFeature placedFeature) {
        Holder<ConfiguredFeature<?, ?>> featureHolder = placedFeature.feature();
        if (featureHolder == null || !featureHolder.isBound()) {
            return new ClassificationResult(FeatureCategory.OTHER, null, null);
        }

        ConfiguredFeature<?, ?> configuredFeature = featureHolder.value();
        Object config = configuredFeature.config();
        ResourceLocation featureId = featureHolder.unwrapKey().map(k -> k.location()).orElse(null);

        // 1. Ore classification using BlockTags and target block states
        if (config instanceof OreConfiguration oreConfig) {
            String oreType = null;
            ResourceLocation oreBlockId = null;

            for (OreConfiguration.TargetBlockState target : oreConfig.targetStates) {
                BlockState targetState = target.state;
                Block block = targetState.getBlock();
                oreBlockId = BuiltInRegistries.BLOCK.getKey(block);

                if (targetState.is(BlockTags.COAL_ORES)) {
                    oreType = "coal";
                } else if (targetState.is(BlockTags.IRON_ORES)) {
                    oreType = "iron";
                } else if (targetState.is(BlockTags.COPPER_ORES)) {
                    oreType = "copper";
                } else if (targetState.is(BlockTags.GOLD_ORES)) {
                    oreType = "gold";
                } else if (targetState.is(BlockTags.REDSTONE_ORES)) {
                    oreType = "redstone";
                } else if (targetState.is(BlockTags.DIAMOND_ORES)) {
                    oreType = "diamond";
                } else if (targetState.is(BlockTags.LAPIS_ORES)) {
                    oreType = "lapis";
                } else if (targetState.is(BlockTags.EMERALD_ORES)) {
                    oreType = "emerald";
                } else if (block == Blocks.NETHER_QUARTZ_ORE) {
                    oreType = "quartz";
                } else if (block == Blocks.ANCIENT_DEBRIS) {
                    oreType = "debris";
                }

                if (oreType != null) {
                    break;
                }
            }

            return new ClassificationResult(FeatureCategory.ORE, oreType, oreBlockId);
        }

        // 2. Tree classification
        if (config instanceof TreeConfiguration) {
            return new ClassificationResult(FeatureCategory.TREE, null, null);
        }

        // 3. Random patch (vegetation, flowers, grass, dead bush)
        if (config instanceof RandomPatchConfiguration patchConfig) {
            Holder<PlacedFeature> innerPlaced = patchConfig.feature();
            if (innerPlaced != null && innerPlaced.isBound()) {
                ConfiguredFeature<?, ?> innerConfigured = innerPlaced.value().feature().value();
                if (innerConfigured.config() instanceof SimpleBlockConfiguration simpleBlock) {
                    BlockState placedState = simpleBlock.toPlace().getState(null, null);
                    Block placedBlock = placedState.getBlock();
                    ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(placedBlock);

                    if (placedState.is(BlockTags.FLOWERS)) {
                        return new ClassificationResult(FeatureCategory.FLOWER, null, blockKey);
                    }
                    if (placedBlock == Blocks.SHORT_GRASS || placedBlock == Blocks.TALL_GRASS ||
                            placedBlock == Blocks.FERN || placedBlock == Blocks.LARGE_FERN) {
                        return new ClassificationResult(FeatureCategory.GRASS, null, blockKey);
                    }
                    if (placedBlock == Blocks.DEAD_BUSH) {
                        return new ClassificationResult(FeatureCategory.DEAD_BUSH, null, blockKey);
                    }
                }
            }
        }

        // 4. Fallback classification by registry ID
        if (featureId != null) {
            String path = featureId.getPath();
            if (path.endsWith("_ore") || path.contains("_ore_")) {
                String oreType = resolveOreTypeFromName(path);
                return new ClassificationResult(FeatureCategory.ORE, oreType, null);
            }
            if (path.contains("tree") || path.contains("oak") || path.contains("spruce") ||
                    path.contains("birch") || path.contains("jungle") || path.contains("acacia") ||
                    path.contains("dark_oak") || path.contains("mangrove") || path.contains("cherry")) {
                return new ClassificationResult(FeatureCategory.TREE, null, null);
            }
            if (path.contains("flower")) {
                return new ClassificationResult(FeatureCategory.FLOWER, null, null);
            }
            if (path.contains("grass") || path.contains("fern")) {
                return new ClassificationResult(FeatureCategory.GRASS, null, null);
            }
            if (path.contains("dead_bush")) {
                return new ClassificationResult(FeatureCategory.DEAD_BUSH, null, null);
            }
        }

        return new ClassificationResult(FeatureCategory.OTHER, null, null);
    }

    private static String resolveOreTypeFromName(String name) {
        if (name.contains("coal")) return "coal";
        if (name.contains("iron")) return "iron";
        if (name.contains("copper")) return "copper";
        if (name.contains("gold")) return "gold";
        if (name.contains("redstone")) return "redstone";
        if (name.contains("diamond")) return "diamond";
        if (name.contains("lapis")) return "lapis";
        if (name.contains("emerald")) return "emerald";
        if (name.contains("quartz")) return "quartz";
        if (name.contains("debris")) return "debris";
        return null;
    }
}
