package zooller.regnumworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import zooller.regnumworld.RegnumWorld;
import zooller.regnumworld.service.RegionalMultiplierService;

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
     * Enables safe in-chunk placement of extra ore veins when ore multiplier > 1.0 without cross-chunk offsets.
     */
    @SerializedName("safe_extra_ore_worldgen_enabled")
    public boolean safeExtraOreWorldgenEnabled = true;

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
                RegnumConfig loaded = GSON.fromJson(reader, RegnumConfig.class);
                if (loaded != null) {
                    validateConfig(loaded);
                    INSTANCE = loaded;
                    RegnumWorld.LOGGER.info("Regnum World configuration loaded and validated successfully.");
                    return;
                }
            } catch (Exception e) {
                RegnumWorld.LOGGER.error("Failed to parse Regnum World configuration! Preserving damaged file on disk to prevent data loss. Error: {}", e.getMessage());
                if (INSTANCE != null) {
                    RegnumWorld.LOGGER.warn("Keeping previously loaded valid configuration in memory.");
                    return;
                }
                RegnumWorld.LOGGER.warn("Falling back to default in-memory configuration without overwriting damaged file.");
                INSTANCE = createDefaultConfig();
                return;
            }
        }

        INSTANCE = createDefaultConfig();
        save();
    }

    public static void validateConfig(RegnumConfig config) {
        if (config == null) return;

        config.bonemealEfficiency = clampNumber("bonemeal_efficiency", config.bonemealEfficiency, 0.0, 1.0, 0.50);
        config.mountainAltitudeMultiplier = clampNumber("mountain_altitude_multiplier", config.mountainAltitudeMultiplier, 0.0, 10.0, 0.5);

        if (config.undergroundMaxY < -64 || config.undergroundMaxY > 320) {
            RegnumWorld.LOGGER.warn("Invalid underground_max_y {}, resetting to 62.", config.undergroundMaxY);
            config.undergroundMaxY = 62;
        }

        if (config.globalOreMultipliers == null) config.globalOreMultipliers = new HashMap<>();
        if (config.biomeGrowth == null) config.biomeGrowth = new HashMap<>();
        if (config.biomeWorldgen == null) config.biomeWorldgen = new HashMap<>();
        if (config.biomeMobs == null) config.biomeMobs = new HashMap<>();
        if (config.biomeFishing == null) config.biomeFishing = new HashMap<>();
        if (config.biomeAnimalGrowth == null) config.biomeAnimalGrowth = new HashMap<>();

        validateDoubleMap("global_ore_multipliers", config.globalOreMultipliers, 0.0, 100.0);
        validateDoubleMap("biome_fishing_modifiers", config.biomeFishing, 0.0, 100.0);
        validateDoubleMap("biome_animal_growth_modifiers", config.biomeAnimalGrowth, 0.0, 100.0);

        for (Map.Entry<String, BiomeGrowthConfig> entry : config.biomeGrowth.entrySet()) {
            BiomeGrowthConfig bg = entry.getValue();
            if (bg != null) {
                bg.defaultMultiplier = clampNumber("biomeGrowth.defaultMultiplier", bg.defaultMultiplier, 0.0, 100.0, 1.0);
                if (bg.crops == null) bg.crops = new HashMap<>();
                validateDoubleMap("biomeGrowth.crops", bg.crops, 0.0, 100.0);
            }
        }

        for (Map.Entry<String, BiomeMobConfig> entry : config.biomeMobs.entrySet()) {
            BiomeMobConfig bm = entry.getValue();
            if (bm != null) {
                bm.defaultMultiplier = clampNumber("biomeMobs.defaultMultiplier", bm.defaultMultiplier, 0.0, 100.0, 1.0);
                bm.hostileMultiplier = clampNumber("biomeMobs.hostileMultiplier", bm.hostileMultiplier, 0.0, 100.0, 1.0);
                bm.creatureMultiplier = clampNumber("biomeMobs.creatureMultiplier", bm.creatureMultiplier, 0.0, 100.0, 1.0);
                bm.waterMultiplier = clampNumber("biomeMobs.waterMultiplier", bm.waterMultiplier, 0.0, 100.0, 1.0);
                bm.ambientMultiplier = clampNumber("biomeMobs.ambientMultiplier", bm.ambientMultiplier, 0.0, 100.0, 1.0);
                if (bm.mobs == null) bm.mobs = new HashMap<>();
                validateDoubleMap("biomeMobs.mobs", bm.mobs, 0.0, 100.0);
            }
        }

        for (Map.Entry<String, BiomeWorldgenConfig> entry : config.biomeWorldgen.entrySet()) {
            BiomeWorldgenConfig bw = entry.getValue();
            if (bw != null) {
                bw.treeMultiplier = clampNumber("biomeWorldgen.treeMultiplier", bw.treeMultiplier, 0.0, 100.0, 1.0);
                bw.flowerMultiplier = clampNumber("biomeWorldgen.flowerMultiplier", bw.flowerMultiplier, 0.0, 100.0, 1.0);
                bw.grassMultiplier = clampNumber("biomeWorldgen.grassMultiplier", bw.grassMultiplier, 0.0, 100.0, 1.0);
                if (bw.vegetationMultipliers == null) bw.vegetationMultipliers = new HashMap<>();
                validateDoubleMap("biomeWorldgen.vegetationMultipliers", bw.vegetationMultipliers, 0.0, 100.0);
                if (bw.oreMultipliers == null) bw.oreMultipliers = new HashMap<>();
                validateDoubleMap("biomeWorldgen.oreMultipliers", bw.oreMultipliers, 0.0, 100.0);
            }
        }
    }

    private static double clampNumber(String field, Double val, double min, double max, double fallback) {
        if (val == null || Double.isNaN(val) || Double.isInfinite(val)) {
            RegnumWorld.LOGGER.warn("Configuration field '{}' had invalid value (null/NaN/Infinity), falling back to {}", field, fallback);
            return fallback;
        }
        if (val < min) {
            RegnumWorld.LOGGER.warn("Configuration field '{}' was below min ({}), clamping to {}", field, min, min);
            return min;
        }
        if (val > max) {
            RegnumWorld.LOGGER.warn("Configuration field '{}' exceeded max ({}), clamping to {}", field, max, max);
            return max;
        }
        return val;
    }

    private static void validateDoubleMap(String mapName, Map<String, Double> map, double min, double max) {
        if (map == null) return;
        map.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().trim().isEmpty());
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            entry.setValue(clampNumber(mapName + "[" + entry.getKey() + "]", entry.getValue(), min, max, 1.0));
        }
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

    public String resolveBiomeKey(ResourceLocation biomeId) {
        return RegionalMultiplierService.resolveBiomeKey(biomeId, this);
    }

    public BiomeGrowthConfig getGrowthConfig(ResourceLocation biomeId) {
        if (biomeId == null) return null;
        return biomeGrowth.get(resolveBiomeKey(biomeId));
    }

    public BiomeWorldgenConfig getWorldgenConfig(ResourceLocation biomeId) {
        if (biomeId == null) return null;
        return biomeWorldgen.get(resolveBiomeKey(biomeId));
    }

    public BiomeMobConfig getMobConfig(ResourceLocation biomeId) {
        if (biomeId == null) return null;
        return biomeMobs.get(resolveBiomeKey(biomeId));
    }

    public double getCropMultiplier(ResourceLocation biomeId, ResourceLocation blockId) {
        return RegionalMultiplierService.computeCropMultiplier(biomeId, blockId, null, false, this);
    }

    public double getTreeMultiplier(ResourceLocation biomeId) {
        BiomeWorldgenConfig config = getWorldgenConfig(biomeId);
        return config != null ? config.treeMultiplier : 1.0;
    }

    public double getFlowerMultiplier(ResourceLocation biomeId) {
        BiomeWorldgenConfig config = getWorldgenConfig(biomeId);
        return config != null ? config.flowerMultiplier : 1.0;
    }

    public double getGrassMultiplier(ResourceLocation biomeId) {
        BiomeWorldgenConfig config = getWorldgenConfig(biomeId);
        return config != null ? config.grassMultiplier : 1.0;
    }

    public double getVegetationMultiplier(ResourceLocation biomeId, String vegType, ResourceLocation blockId) {
        BiomeWorldgenConfig config = getWorldgenConfig(biomeId);
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
        return RegionalMultiplierService.computeOreMultiplier(biomeId, oreBlockId, oreCategory, this);
    }

    public double getMobSpawnMultiplier(ResourceLocation biomeId, MobCategory category, ResourceLocation mobId) {
        return RegionalMultiplierService.computeMobSpawnMultiplier(biomeId, category, mobId, this);
    }

    public double getFishingMultiplier(ResourceLocation biomeId) {
        return RegionalMultiplierService.computeFishingMultiplier(biomeId, this);
    }

    public double getAnimalGrowthMultiplier(ResourceLocation biomeId) {
        return RegionalMultiplierService.computeAnimalGrowthMultiplier(biomeId, this);
    }

    public static RegnumConfig createDefaultConfig() {
        RegnumConfig config = new RegnumConfig();

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
