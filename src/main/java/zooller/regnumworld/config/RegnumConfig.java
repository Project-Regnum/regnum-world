package zooller.regnumworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import zooller.regnumworld.RegnumWorld;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class RegnumConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RegnumConfig INSTANCE;

    /**
     * Enable road speed boost for players and mounts (native attribute modifier, no potion effect).
     */
    @SerializedName("roads_enabled")
    public boolean roadsEnabled = true;

    /**
     * Speed bonus for Tier 1 roads (Dirt Path, Gravel, Cobblestone, Sandstone, Planks).
     * 0.08 = subtle +8% movement speed.
     */
    @SerializedName("road_tier1_speed_bonus")
    public double roadTier1SpeedBonus = 0.08;

    /**
     * Speed bonus for Tier 2 roads (Imperial Stone Bricks, Polished Deepslate, Smooth Stone, Bricks).
     * 0.15 = subtle +15% movement speed.
     */
    @SerializedName("road_tier2_speed_bonus")
    public double roadTier2SpeedBonus = 0.15;

    /**
     * Bone meal efficiency (0.5 = 50% chance of success, nerfed by half).
     */
    @SerializedName("bonemeal_efficiency")
    public double bonemealEfficiency = 0.50;

    /**
     * Prohibit bone meal from forcing crop growth in underground mines or dark caves.
     */
    @SerializedName("block_bonemeal_underground")
    public boolean blockBonemealUnderground = true;

    /**
     * If true, crops will not grow underground / in mines.
     */
    @SerializedName("block_underground_crop_growth")
    public boolean blockUndergroundCropGrowth = true;

    /**
     * Max Y level considered underground (default 62 = sea level).
     * Any crop below this Y coordinate will not grow.
     */
    @SerializedName("underground_max_y")
    public int undergroundMaxY = 62;

    /**
     * If true, crops in caves without sky access will not grow even above undergroundMaxY.
     */
    @SerializedName("block_cave_crop_growth_without_sky")
    public boolean blockCaveCropGrowthWithoutSky = true;

    /**
     * If true, high altitude in mountains gives an extra growth debuff.
     */
    @SerializedName("mountain_altitude_debuff_enabled")
    public boolean mountainAltitudeDebuffEnabled = true;

    @SerializedName("mountain_altitude_start_y")
    public int mountainAltitudeStartY = 95;

    @SerializedName("mountain_altitude_multiplier")
    public double mountainAltitudeMultiplier = 0.5;

    /**
     * Global multipliers for ores across all biomes unless overridden by a specific biome.
     */
    @SerializedName("global_ore_multipliers")
    public Map<String, Double> globalOreMultipliers = new HashMap<>();

    @SerializedName("biome_growth_modifiers")
    public Map<String, BiomeGrowthConfig> biomeGrowth = new HashMap<>();

    @SerializedName("biome_worldgen_modifiers")
    public Map<String, BiomeWorldgenConfig> biomeWorldgen = new HashMap<>();

    @SerializedName("biome_mob_modifiers")
    public Map<String, BiomeMobConfig> biomeMobs = new HashMap<>();

    @SerializedName("biome_fishing_modifiers")
    public Map<String, Double> biomeFishing = new HashMap<>();

    @SerializedName("biome_animal_growth_modifiers")
    public Map<String, Double> biomeAnimalGrowth = new HashMap<>();

    public static RegnumConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        File configFile = configDir.resolve("regnum_world.json").toFile();

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                INSTANCE = GSON.fromJson(reader, RegnumConfig.class);
                if (INSTANCE != null) {
                    RegnumWorld.LOGGER.info("Regnum World configuration loaded successfully.");
                    return;
                }
            } catch (Exception e) {
                RegnumWorld.LOGGER.error("Failed to load Regnum World configuration, creating default.", e);
            }
        }

        INSTANCE = createDefaultConfig();
        save();
    }

    public static void save() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        File configFile = configDir.resolve("regnum_world.json").toFile();

        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(INSTANCE, writer);
                RegnumWorld.LOGGER.info("Regnum World configuration saved to {}", configFile.getAbsolutePath());
            }
        } catch (IOException e) {
            RegnumWorld.LOGGER.error("Failed to save Regnum World configuration.", e);
        }
    }

    public double getCropMultiplier(ResourceLocation biomeId, ResourceLocation blockId) {
        if (biomeId == null || blockId == null) {
            return 1.0;
        }

        String biomeKey = biomeId.toString();
        BiomeGrowthConfig config = biomeGrowth.get(biomeKey);
        if (config == null) {
            return 1.0;
        }

        String blockKey = blockId.toString();
        if (config.crops.containsKey(blockKey)) {
            return config.crops.get(blockKey);
        }

        String path = blockId.getPath();
        if (config.crops.containsKey(path)) {
            return config.crops.get(path);
        }

        return config.defaultMultiplier;
    }

    public double getTreeMultiplier(ResourceLocation biomeId) {
        if (biomeId == null) {
            return 1.0;
        }
        BiomeWorldgenConfig config = biomeWorldgen.get(biomeId.toString());
        return config != null ? config.treeMultiplier : 1.0;
    }

    public double getFlowerMultiplier(ResourceLocation biomeId) {
        if (biomeId == null) {
            return 1.0;
        }
        BiomeWorldgenConfig config = biomeWorldgen.get(biomeId.toString());
        return config != null ? config.flowerMultiplier : 1.0;
    }

    public double getGrassMultiplier(ResourceLocation biomeId) {
        if (biomeId == null) {
            return 1.0;
        }
        BiomeWorldgenConfig config = biomeWorldgen.get(biomeId.toString());
        return config != null ? config.grassMultiplier : 1.0;
    }

    public double getVegetationMultiplier(ResourceLocation biomeId, String vegType, ResourceLocation blockId) {
        if (biomeId == null) {
            return 1.0;
        }
        BiomeWorldgenConfig config = biomeWorldgen.get(biomeId.toString());
        if (config == null) {
            return 1.0;
        }

        if (blockId != null && config.vegetationMultipliers.containsKey(blockId.toString())) {
            return config.vegetationMultipliers.get(blockId.toString());
        }
        if (blockId != null && config.vegetationMultipliers.containsKey(blockId.getPath())) {
            return config.vegetationMultipliers.get(blockId.getPath());
        }
        if (vegType != null && config.vegetationMultipliers.containsKey(vegType.toLowerCase())) {
            return config.vegetationMultipliers.get(vegType.toLowerCase());
        }

        return 1.0;
    }

    public double getOreMultiplier(ResourceLocation biomeId, String oreCategory, ResourceLocation oreBlockId) {
        if (biomeId != null) {
            String biomeKey = biomeId.toString();
            BiomeWorldgenConfig config = biomeWorldgen.get(biomeKey);
            if (config != null) {
                if (oreBlockId != null && config.oreMultipliers.containsKey(oreBlockId.toString())) {
                    return config.oreMultipliers.get(oreBlockId.toString());
                }

                if (oreBlockId != null && config.oreMultipliers.containsKey(oreBlockId.getPath())) {
                    return config.oreMultipliers.get(oreBlockId.getPath());
                }

                if (oreCategory != null && config.oreMultipliers.containsKey(oreCategory.toLowerCase())) {
                    return config.oreMultipliers.get(oreCategory.toLowerCase());
                }
            }
        }

        // Global fallback for ores
        if (oreBlockId != null && globalOreMultipliers.containsKey(oreBlockId.toString())) {
            return globalOreMultipliers.get(oreBlockId.toString());
        }

        if (oreBlockId != null && globalOreMultipliers.containsKey(oreBlockId.getPath())) {
            return globalOreMultipliers.get(oreBlockId.getPath());
        }

        if (oreCategory != null && globalOreMultipliers.containsKey(oreCategory.toLowerCase())) {
            return globalOreMultipliers.get(oreCategory.toLowerCase());
        }

        return 1.0;
    }

    public double getMobSpawnMultiplier(ResourceLocation biomeId, MobCategory category, ResourceLocation mobId) {
        if (biomeId == null) {
            return 1.0;
        }

        BiomeMobConfig config = biomeMobs.get(biomeId.toString());
        if (config == null) {
            return 1.0;
        }

        if (mobId != null) {
            if (config.mobs.containsKey(mobId.toString())) {
                return config.mobs.get(mobId.toString());
            }
            if (config.mobs.containsKey(mobId.getPath())) {
                return config.mobs.get(mobId.getPath());
            }
        }

        if (category == MobCategory.MONSTER) {
            return config.hostileMultiplier;
        } else if (category == MobCategory.CREATURE) {
            return config.creatureMultiplier;
        } else if (category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT) {
            return config.waterMultiplier;
        } else if (category == MobCategory.AMBIENT) {
            return config.ambientMultiplier;
        }

        return config.defaultMultiplier;
    }

    public double getFishingMultiplier(ResourceLocation biomeId) {
        if (biomeId == null) {
            return 1.0;
        }
        return biomeFishing.getOrDefault(biomeId.toString(), 1.0);
    }

    public double getAnimalGrowthMultiplier(ResourceLocation biomeId) {
        if (biomeId == null) {
            return 1.0;
        }
        return biomeAnimalGrowth.getOrDefault(biomeId.toString(), 1.0);
    }

    public static RegnumConfig createDefaultConfig() {
        RegnumConfig config = new RegnumConfig();

        config.roadsEnabled = true;
        config.roadTier1SpeedBonus = 0.08; // subtle +8% speed (no potion effects)
        config.roadTier2SpeedBonus = 0.15; // subtle +15% speed (no potion effects)

        config.bonemealEfficiency = 0.50; // nerfed by 50%
        config.blockBonemealUnderground = true;

        config.blockUndergroundCropGrowth = true;
        config.undergroundMaxY = 62;
        config.blockCaveCropGrowthWithoutSky = true;
        config.mountainAltitudeDebuffEnabled = true;
        config.mountainAltitudeStartY = 95;
        config.mountainAltitudeMultiplier = 0.5;

        // ==========================================
        // GLOBAL ORE MULTIPLIERS (UBER-RARE DIAMONDS)
        // ==========================================
        config.globalOreMultipliers.put("diamond", 0.10);

        // ==========================================
        // 1. CROP GROWTH REGIONAL ADVANTAGES & DEBUFFS
        // ==========================================

        config.biomeGrowth.put("minecraft:desert", new BiomeGrowthConfig(0.2)
                .addCrop("minecraft:cactus", 3.0)
                .addCrop("minecraft:sugar_cane", 0.6)
                .addCrop("minecraft:wheat", 0.1)
                .addCrop("minecraft:carrots", 0.1)
                .addCrop("minecraft:potatoes", 0.1)
                .addCrop("minecraft:beetroots", 0.1));

        config.biomeGrowth.put("minecraft:snowy_plains", new BiomeGrowthConfig(0.1)
                .addCrop("minecraft:wheat", 0.1)
                .addCrop("minecraft:carrots", 0.1)
                .addCrop("minecraft:potatoes", 0.1)
                .addCrop("minecraft:beetroots", 0.1));

        config.biomeGrowth.put("minecraft:ice_spikes", new BiomeGrowthConfig(0.0)
                .addCrop("minecraft:wheat", 0.0)
                .addCrop("minecraft:carrots", 0.0)
                .addCrop("minecraft:potatoes", 0.0));

        config.biomeGrowth.put("minecraft:snowy_taiga", new BiomeGrowthConfig(0.2)
                .addCrop("minecraft:sweet_berry_bush", 1.5)
                .addCrop("minecraft:spruce_sapling", 1.5)
                .addCrop("minecraft:wheat", 0.1)
                .addCrop("minecraft:potatoes", 0.1));

        config.biomeGrowth.put("minecraft:snowy_beach", new BiomeGrowthConfig(0.1)
                .addCrop("minecraft:wheat", 0.1));

        config.biomeGrowth.put("minecraft:snowy_slopes", new BiomeGrowthConfig(0.1)
                .addCrop("minecraft:wheat", 0.05));

        config.biomeGrowth.put("minecraft:frozen_peaks", new BiomeGrowthConfig(0.0)
                .addCrop("minecraft:wheat", 0.0)
                .addCrop("minecraft:carrots", 0.0));

        config.biomeGrowth.put("minecraft:jagged_peaks", new BiomeGrowthConfig(0.0)
                .addCrop("minecraft:wheat", 0.0)
                .addCrop("minecraft:carrots", 0.0)
                .addCrop("minecraft:potatoes", 0.0));

        config.biomeGrowth.put("minecraft:stony_peaks", new BiomeGrowthConfig(0.1)
                .addCrop("minecraft:wheat", 0.1)
                .addCrop("minecraft:carrots", 0.1));

        config.biomeGrowth.put("minecraft:windswept_hills", new BiomeGrowthConfig(0.3)
                .addCrop("minecraft:wheat", 0.2)
                .addCrop("minecraft:carrots", 0.2));

        config.biomeGrowth.put("minecraft:plains", new BiomeGrowthConfig(1.4)
                .addCrop("minecraft:wheat", 2.2)
                .addCrop("minecraft:carrots", 1.6)
                .addCrop("minecraft:potatoes", 1.6)
                .addCrop("minecraft:beetroots", 1.6));

        config.biomeGrowth.put("minecraft:meadow", new BiomeGrowthConfig(1.3)
                .addCrop("minecraft:wheat", 2.0)
                .addCrop("minecraft:carrots", 1.5)
                .addCrop("minecraft:potatoes", 1.5));

        config.biomeGrowth.put("minecraft:jungle", new BiomeGrowthConfig(1.5)
                .addCrop("minecraft:cocoa", 2.5)
                .addCrop("minecraft:bamboo", 2.5)
                .addCrop("minecraft:melon_stem", 2.0)
                .addCrop("minecraft:sugar_cane", 1.8));

        config.biomeGrowth.put("minecraft:forest", new BiomeGrowthConfig(1.2)
                .addCrop("minecraft:sweet_berry_bush", 2.0)
                .addCrop("minecraft:oak_sapling", 2.0)
                .addCrop("minecraft:birch_sapling", 2.0));

        config.biomeGrowth.put("minecraft:dark_forest", new BiomeGrowthConfig(1.2)
                .addCrop("minecraft:dark_oak_sapling", 2.0)
                .addCrop("minecraft:pumpkin_stem", 1.8));

        config.biomeGrowth.put("minecraft:swamp", new BiomeGrowthConfig(0.8)
                .addCrop("minecraft:sugar_cane", 2.5)
                .addCrop("minecraft:wheat", 0.5));

        // ==========================================
        // 2. WORLDGEN ORES & VEGETATION
        // ==========================================

        config.biomeWorldgen.put("minecraft:badlands", new BiomeWorldgenConfig(0.2, 0.1, 0.1)
                .addVegetation("dead_bush", 2.0)
                .addVegetation("cactus", 1.5)
                .addOre("gold", 3.5)
                .addOre("lapis", 2.6)
                .addOre("copper", 2.5)
                .addOre("redstone", 1.8)
                .addOre("iron", 0.7)
                .addOre("coal", 0.5)
                .addOre("diamond", 0.08));

        config.biomeWorldgen.put("minecraft:eroded_badlands", new BiomeWorldgenConfig(0.1, 0.05, 0.05)
                .addOre("gold", 3.8)
                .addOre("lapis", 2.8)
                .addOre("copper", 2.5)
                .addOre("redstone", 2.0)
                .addOre("iron", 0.6)
                .addOre("coal", 0.4));

        config.biomeWorldgen.put("minecraft:wooded_badlands", new BiomeWorldgenConfig(0.6, 0.2, 0.3)
                .addOre("gold", 3.0)
                .addOre("lapis", 2.2)
                .addOre("copper", 2.0));

        config.biomeWorldgen.put("minecraft:desert", new BiomeWorldgenConfig(0.0, 0.0, 0.05)
                .addVegetation("dead_bush", 2.5)
                .addVegetation("cactus", 2.0)
                .addOre("gold", 2.5)
                .addOre("lapis", 2.4)
                .addOre("copper", 2.2)
                .addOre("redstone", 1.8)
                .addOre("iron", 0.6)
                .addOre("coal", 0.4)
                .addOre("diamond", 0.05));

        config.biomeWorldgen.put("minecraft:stony_peaks", new BiomeWorldgenConfig(0.0, 0.1, 0.1)
                .addOre("emerald", 3.5)
                .addOre("iron", 2.2)
                .addOre("coal", 2.2)
                .addOre("lapis", 2.0)
                .addOre("copper", 1.8)
                .addOre("diamond", 0.05));

        config.biomeWorldgen.put("minecraft:jagged_peaks", new BiomeWorldgenConfig(0.0, 0.0, 0.05)
                .addOre("emerald", 4.0)
                .addOre("iron", 2.4)
                .addOre("coal", 2.2)
                .addOre("lapis", 2.0)
                .addOre("diamond", 0.05));

        config.biomeWorldgen.put("minecraft:frozen_peaks", new BiomeWorldgenConfig(0.0, 0.0, 0.05)
                .addOre("emerald", 3.5)
                .addOre("iron", 2.0)
                .addOre("coal", 1.8)
                .addOre("lapis", 1.8));

        config.biomeWorldgen.put("minecraft:windswept_hills", new BiomeWorldgenConfig(0.7, 0.3, 0.5)
                .addOre("emerald", 3.0)
                .addOre("iron", 2.0)
                .addOre("coal", 2.0)
                .addOre("copper", 1.8)
                .addOre("lapis", 1.6));

        config.biomeWorldgen.put("minecraft:windswept_gravelly_hills", new BiomeWorldgenConfig(0.4, 0.2, 0.3)
                .addOre("emerald", 3.0)
                .addOre("iron", 2.0)
                .addOre("coal", 2.0));

        config.biomeWorldgen.put("minecraft:swamp", new BiomeWorldgenConfig(1.2, 1.5, 1.4)
                .addVegetation("sugar_cane", 2.0)
                .addVegetation("lilypad", 2.0)
                .addOre("iron", 2.5)
                .addOre("coal", 2.0)
                .addOre("copper", 0.8)
                .addOre("gold", 0.4)
                .addOre("lapis", 0.5)
                .addOre("redstone", 0.5));

        config.biomeWorldgen.put("minecraft:mangrove_swamp", new BiomeWorldgenConfig(1.5, 1.3, 1.3)
                .addOre("iron", 2.5)
                .addOre("coal", 1.8)
                .addOre("gold", 0.4));

        config.biomeWorldgen.put("minecraft:forest", new BiomeWorldgenConfig(1.8, 1.2, 1.4)
                .addOre("coal", 2.4)
                .addOre("iron", 1.4)
                .addOre("copper", 1.0)
                .addOre("lapis", 0.7)
                .addOre("gold", 0.5));

        config.biomeWorldgen.put("minecraft:dark_forest", new BiomeWorldgenConfig(2.0, 1.5, 1.3)
                .addVegetation("mushroom", 2.5)
                .addOre("coal", 2.6)
                .addOre("iron", 1.5)
                .addOre("copper", 1.0)
                .addOre("lapis", 0.8)
                .addOre("gold", 0.5));

        config.biomeWorldgen.put("minecraft:birch_forest", new BiomeWorldgenConfig(1.6, 1.3, 1.4)
                .addOre("coal", 2.2)
                .addOre("iron", 1.3));

        config.biomeWorldgen.put("minecraft:flower_forest", new BiomeWorldgenConfig(1.3, 3.0, 1.5)
                .addOre("coal", 1.5)
                .addOre("copper", 1.2)
                .addOre("lapis", 1.2));

        config.biomeWorldgen.put("minecraft:jungle", new BiomeWorldgenConfig(2.2, 1.5, 1.8)
                .addVegetation("bamboo", 2.0)
                .addVegetation("melon", 2.0)
                .addOre("gold", 2.2)
                .addOre("redstone", 2.2)
                .addOre("coal", 1.8)
                .addOre("copper", 1.6)
                .addOre("lapis", 1.4)
                .addOre("diamond", 0.15));

        config.biomeWorldgen.put("minecraft:sparse_jungle", new BiomeWorldgenConfig(1.6, 1.3, 1.5)
                .addOre("gold", 2.0)
                .addOre("redstone", 1.8)
                .addOre("coal", 1.6));

        config.biomeWorldgen.put("minecraft:plains", new BiomeWorldgenConfig(0.8, 1.2, 1.5)
                .addOre("coal", 1.2)
                .addOre("iron", 1.1)
                .addOre("copper", 1.0)
                .addOre("gold", 0.6)
                .addOre("lapis", 0.8));

        config.biomeWorldgen.put("minecraft:meadow", new BiomeWorldgenConfig(0.3, 2.5, 1.5)
                .addOre("emerald", 2.0)
                .addOre("iron", 1.3)
                .addOre("coal", 1.3)
                .addOre("lapis", 1.2));

        config.biomeWorldgen.put("minecraft:snowy_plains", new BiomeWorldgenConfig(0.2, 0.1, 0.1)
                .addOre("iron", 1.4)
                .addOre("coal", 0.5)
                .addOre("copper", 0.5)
                .addOre("gold", 0.3)
                .addOre("lapis", 0.4)
                .addOre("diamond", 0.05));

        config.biomeWorldgen.put("minecraft:ice_spikes", new BiomeWorldgenConfig(0.0, 0.05, 0.05)
                .addOre("iron", 1.2)
                .addOre("lapis", 0.3)
                .addOre("coal", 0.4)
                .addOre("diamond", 0.05));

        // ==========================================
        // 3. MOB SPAWN RATES BY BIOME
        // ==========================================

        config.biomeMobs.put("minecraft:plains", new BiomeMobConfig(1.0, 2.2)
                .addMob("minecraft:cow", 2.5)
                .addMob("minecraft:sheep", 2.5)
                .addMob("minecraft:pig", 2.2)
                .addMob("minecraft:chicken", 2.2)
                .addMob("minecraft:horse", 2.0));

        config.biomeMobs.put("minecraft:meadow", new BiomeMobConfig(0.8, 2.5)
                .addMob("minecraft:sheep", 3.0)
                .addMob("minecraft:rabbit", 2.5)
                .addMob("minecraft:donkey", 2.0));

        config.biomeMobs.put("minecraft:forest", new BiomeMobConfig(1.2, 1.8)
                .addMob("minecraft:wolf", 2.0)
                .addMob("minecraft:pig", 1.8));

        config.biomeMobs.put("minecraft:flower_forest", new BiomeMobConfig(1.0, 2.0)
                .addMob("minecraft:rabbit", 2.5)
                .addMob("minecraft:bee", 2.5));

        config.biomeMobs.put("minecraft:jungle", new BiomeMobConfig(1.3, 2.0)
                .addMob("minecraft:ocelot", 2.5)
                .addMob("minecraft:parrot", 2.5)
                .addMob("minecraft:panda", 2.0));

        config.biomeMobs.put("minecraft:savanna", new BiomeMobConfig(1.1, 2.0)
                .addMob("minecraft:horse", 2.2)
                .addMob("minecraft:llama", 2.2));

        config.biomeMobs.put("minecraft:desert", new BiomeMobConfig(1.6, 0.15)
                .addMob("minecraft:husk", 2.5)
                .addMob("minecraft:spider", 1.5)
                .addMob("minecraft:camel", 2.0));

        config.biomeMobs.put("minecraft:snowy_plains", new BiomeMobConfig(1.2, 0.2)
                .addMob("minecraft:polar_bear", 2.0)
                .addMob("minecraft:stray", 2.5)
                .addMob("minecraft:rabbit", 1.2));

        config.biomeMobs.put("minecraft:ice_spikes", new BiomeMobConfig(1.3, 0.1)
                .addMob("minecraft:polar_bear", 2.5)
                .addMob("minecraft:stray", 3.0));

        config.biomeMobs.put("minecraft:snowy_taiga", new BiomeMobConfig(1.1, 0.5)
                .addMob("minecraft:wolf", 2.0)
                .addMob("minecraft:fox", 2.0));

        config.biomeMobs.put("minecraft:stony_peaks", new BiomeMobConfig(1.2, 0.3)
                .addMob("minecraft:goat", 2.5));

        config.biomeMobs.put("minecraft:jagged_peaks", new BiomeMobConfig(1.2, 0.3)
                .addMob("minecraft:goat", 3.0));

        config.biomeMobs.put("minecraft:swamp", new BiomeMobConfig(1.3, 1.2)
                .addMob("minecraft:slime", 2.5)
                .addMob("minecraft:witch", 2.0)
                .addMob("minecraft:frog", 2.5));

        // ==========================================
        // 4. REGIONAL FISHING SPEED MODIFIERS
        // ==========================================

        config.biomeFishing.put("minecraft:river", 1.35);
        config.biomeFishing.put("minecraft:ocean", 1.15);
        config.biomeFishing.put("minecraft:warm_ocean", 1.50);
        config.biomeFishing.put("minecraft:lukewarm_ocean", 1.30);
        config.biomeFishing.put("minecraft:deep_ocean", 1.20);
        config.biomeFishing.put("minecraft:deep_lukewarm_ocean", 1.30);
        config.biomeFishing.put("minecraft:swamp", 1.15);
        config.biomeFishing.put("minecraft:mangrove_swamp", 1.20);
        config.biomeFishing.put("minecraft:jungle", 1.15);

        config.biomeFishing.put("minecraft:desert", 0.3);
        config.biomeFishing.put("minecraft:badlands", 0.3);
        config.biomeFishing.put("minecraft:snowy_plains", 0.3);
        config.biomeFishing.put("minecraft:ice_spikes", 0.2);
        config.biomeFishing.put("minecraft:frozen_river", 0.4);
        config.biomeFishing.put("minecraft:frozen_ocean", 0.4);
        config.biomeFishing.put("minecraft:deep_frozen_ocean", 0.4);
        config.biomeFishing.put("minecraft:snowy_taiga", 0.4);
        config.biomeFishing.put("minecraft:snowy_beach", 0.4);

        // ==========================================
        // 5. REGIONAL ANIMAL HUSBANDRY
        // ==========================================

        config.biomeAnimalGrowth.put("minecraft:plains", 1.8);
        config.biomeAnimalGrowth.put("minecraft:meadow", 2.0);
        config.biomeAnimalGrowth.put("minecraft:forest", 1.4);
        config.biomeAnimalGrowth.put("minecraft:flower_forest", 1.5);
        config.biomeAnimalGrowth.put("minecraft:savanna", 1.6);
        config.biomeAnimalGrowth.put("minecraft:jungle", 1.5);

        config.biomeAnimalGrowth.put("minecraft:desert", 0.4);
        config.biomeAnimalGrowth.put("minecraft:badlands", 0.5);
        config.biomeAnimalGrowth.put("minecraft:snowy_plains", 0.3);
        config.biomeAnimalGrowth.put("minecraft:ice_spikes", 0.2);
        config.biomeAnimalGrowth.put("minecraft:snowy_taiga", 0.6);
        config.biomeAnimalGrowth.put("minecraft:stony_peaks", 0.5);
        config.biomeAnimalGrowth.put("minecraft:jagged_peaks", 0.4);
        config.biomeAnimalGrowth.put("minecraft:frozen_peaks", 0.3);

        return config;
    }
}
