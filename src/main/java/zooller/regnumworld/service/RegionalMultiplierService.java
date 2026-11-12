package zooller.regnumworld.service;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import zooller.regnumworld.config.BiomeGrowthConfig;
import zooller.regnumworld.config.BiomeMobConfig;
import zooller.regnumworld.config.BiomeWorldgenConfig;
import zooller.regnumworld.config.RegnumConfig;

public class RegionalMultiplierService {

    /**
     * Resolves a biome ID to its corresponding configuration key or climate archetype.
     * Pure function with no side effects.
     */
    public static String resolveBiomeKey(ResourceLocation biomeId, RegnumConfig config) {
        if (biomeId == null) {
            return "";
        }
        String fullKey = biomeId.toString();
        if (config.biomeGrowth.containsKey(fullKey) || config.biomeWorldgen.containsKey(fullKey) || config.biomeMobs.containsKey(fullKey)) {
            return fullKey;
        }

        String path = biomeId.getPath().toLowerCase();
        int lastSlash = path.lastIndexOf('/');
        String name = (lastSlash >= 0 && lastSlash < path.length() - 1) ? path.substring(lastSlash + 1) : path;

        String terraDirect = "terra:overworld/overworld/" + name;
        if (config.biomeGrowth.containsKey(terraDirect) || config.biomeWorldgen.containsKey(terraDirect) || config.biomeMobs.containsKey(terraDirect)) {
            return terraDirect;
        }
        String terraShort = "terra:" + name;
        if (config.biomeGrowth.containsKey(terraShort) || config.biomeWorldgen.containsKey(terraShort) || config.biomeMobs.containsKey(terraShort)) {
            return terraShort;
        }
        if (config.biomeGrowth.containsKey(name) || config.biomeWorldgen.containsKey(name) || config.biomeMobs.containsKey(name)) {
            return name;
        }

        // Thematic climate classification for Terra & modded biomes
        if (name.contains("tundra") || name.contains("frozen") || name.contains("ice") || name.contains("polar") || name.contains("iceberg")) {
            return "minecraft:snowy_plains";
        }
        if (name.contains("desert") || name.contains("xeric") || name.contains("arid") || name.contains("salt")) {
            return "minecraft:desert";
        }
        if (name.contains("badlands") || name.contains("mesa") || name.contains("terracotta") || name.contains("buttes")) {
            return "minecraft:badlands";
        }
        if (name.contains("jungle") || name.contains("rainforest") || name.contains("monsoon")) {
            return "minecraft:jungle";
        }
        if (name.contains("savanna")) {
            return "minecraft:savanna";
        }
        if (name.contains("taiga") || name.contains("redwood") || name.contains("evergreen") || name.contains("boreal") || name.contains("yellowstone")) {
            return "minecraft:taiga";
        }
        if (name.contains("swamp") || name.contains("marsh") || name.contains("wetlands")) {
            return "minecraft:swamp";
        }
        if (name.contains("forest") || name.contains("wooded") || name.contains("autumnal") || name.contains("birch") || name.contains("eucalyptus")) {
            return "minecraft:forest";
        }
        if (name.contains("mountain") || name.contains("highlands") || name.contains("peaks") || name.contains("cliffs") || name.contains("cerros")) {
            return "minecraft:windswept_hills";
        }
        if (name.contains("volcano") || name.contains("caldera")) {
            return "minecraft:nether_wastes";
        }
        if (name.contains("ocean") || name.contains("sea") || name.contains("beach") || name.contains("archipelago")) {
            return "minecraft:ocean";
        }
        if (name.contains("plains") || name.contains("prairie") || name.contains("steppe") || name.contains("meadow") || name.contains("chaparral")) {
            return "minecraft:plains";
        }
        if (name.contains("cave") || name.contains("dark")) {
            return "minecraft:lush_caves";
        }

        return fullKey;
    }

    /**
     * Computes the regional crop multiplier, taking into account biome, block ID,
     * optional altitude debuff and underground check.
     * Pure calculation without world mutations.
     */
    public static double computeCropMultiplier(
            ResourceLocation biomeId,
            ResourceLocation blockId,
            BlockPos pos,
            boolean isUnderground,
            RegnumConfig config
    ) {
        if (config.blockUndergroundCropGrowth && isUnderground) {
            return 0.0;
        }

        if (biomeId == null || blockId == null) {
            return 1.0;
        }

        String resolvedBiome = resolveBiomeKey(biomeId, config);
        BiomeGrowthConfig growthConfig = config.biomeGrowth.get(resolvedBiome);

        double multiplier = 1.0;
        if (growthConfig != null) {
            String blockKey = blockId.toString();
            if (growthConfig.crops.containsKey(blockKey)) {
                multiplier = growthConfig.crops.get(blockKey);
            } else if (growthConfig.crops.containsKey(blockId.getPath())) {
                multiplier = growthConfig.crops.get(blockId.getPath());
            } else {
                multiplier = growthConfig.defaultMultiplier;
            }
        }

        if (pos != null && config.mountainAltitudeDebuffEnabled && pos.getY() >= config.mountainAltitudeStartY) {
            multiplier *= config.mountainAltitudeMultiplier;
        }

        return Math.max(0.0, multiplier);
    }

    /**
     * Pure calculation for ore generation multiplier.
     */
    public static double computeOreMultiplier(
            ResourceLocation biomeId,
            ResourceLocation oreBlockId,
            String oreCategory,
            RegnumConfig config
    ) {
        if (biomeId != null) {
            String resolvedBiome = resolveBiomeKey(biomeId, config);
            BiomeWorldgenConfig worldgenConfig = config.biomeWorldgen.get(resolvedBiome);
            if (worldgenConfig != null) {
                if (oreBlockId != null && worldgenConfig.oreMultipliers.containsKey(oreBlockId.toString())) {
                    return worldgenConfig.oreMultipliers.get(oreBlockId.toString());
                }
                if (oreBlockId != null && worldgenConfig.oreMultipliers.containsKey(oreBlockId.getPath())) {
                    return worldgenConfig.oreMultipliers.get(oreBlockId.getPath());
                }
                if (oreCategory != null && worldgenConfig.oreMultipliers.containsKey(oreCategory.toLowerCase())) {
                    return worldgenConfig.oreMultipliers.get(oreCategory.toLowerCase());
                }
            }
        }

        // Global fallback
        if (oreBlockId != null && config.globalOreMultipliers.containsKey(oreBlockId.toString())) {
            return config.globalOreMultipliers.get(oreBlockId.toString());
        }
        if (oreBlockId != null && config.globalOreMultipliers.containsKey(oreBlockId.getPath())) {
            return config.globalOreMultipliers.get(oreBlockId.getPath());
        }
        if (oreCategory != null && config.globalOreMultipliers.containsKey(oreCategory.toLowerCase())) {
            return config.globalOreMultipliers.get(oreCategory.toLowerCase());
        }

        return 1.0;
    }

    /**
     * Pure calculation for mob spawning multiplier.
     */
    public static double computeMobSpawnMultiplier(
            ResourceLocation biomeId,
            MobCategory category,
            ResourceLocation mobId,
            RegnumConfig config
    ) {
        if (biomeId == null) {
            return 1.0;
        }

        String resolvedBiome = resolveBiomeKey(biomeId, config);
        BiomeMobConfig mobConfig = config.biomeMobs.get(resolvedBiome);
        if (mobConfig == null) {
            return 1.0;
        }

        if (mobId != null) {
            String fullKey = mobId.toString().toLowerCase();
            if (mobConfig.mobs.containsKey(fullKey)) {
                return mobConfig.mobs.get(fullKey);
            }
            String pathKey = mobId.getPath().toLowerCase();
            if (mobConfig.mobs.containsKey(pathKey)) {
                return mobConfig.mobs.get(pathKey);
            }
        }

        if (category != null) {
            return switch (category) {
                case MONSTER -> mobConfig.hostileMultiplier;
                case CREATURE -> mobConfig.creatureMultiplier;
                case WATER_CREATURE, WATER_AMBIENT, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> mobConfig.waterMultiplier;
                case AMBIENT -> mobConfig.ambientMultiplier;
                default -> mobConfig.defaultMultiplier;
            };
        }

        return mobConfig.defaultMultiplier;
    }

    /**
     * Pure calculation for animal maturation / breeding speed multiplier.
     */
    public static double computeAnimalGrowthMultiplier(ResourceLocation biomeId, RegnumConfig config) {
        if (biomeId == null) {
            return 1.0;
        }
        String resolvedBiome = resolveBiomeKey(biomeId, config);
        return config.biomeAnimalGrowth.getOrDefault(resolvedBiome, 1.0);
    }

    /**
     * Pure calculation for fishing bite speed multiplier.
     */
    public static double computeFishingMultiplier(ResourceLocation biomeId, RegnumConfig config) {
        if (biomeId == null) {
            return 1.0;
        }
        String resolvedBiome = resolveBiomeKey(biomeId, config);
        return config.biomeFishing.getOrDefault(resolvedBiome, 1.0);
    }
}
