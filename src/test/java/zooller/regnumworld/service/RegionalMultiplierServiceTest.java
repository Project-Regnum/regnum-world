package zooller.regnumworld.service;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zooller.regnumworld.config.BiomeGrowthConfig;
import zooller.regnumworld.config.BiomeMobConfig;
import zooller.regnumworld.config.BiomeWorldgenConfig;
import zooller.regnumworld.config.RegnumConfig;

import static org.junit.jupiter.api.Assertions.*;

public class RegionalMultiplierServiceTest {
    private RegnumConfig config;

    @BeforeEach
    public void setup() {
        config = new RegnumConfig();
        config.mountainAltitudeDebuffEnabled = true;
        config.mountainAltitudeStartY = 95;
        config.mountainAltitudeMultiplier = 0.5;

        // Configure Plains
        BiomeGrowthConfig plainsGrowth = new BiomeGrowthConfig(1.2);
        plainsGrowth.addCrop("minecraft:wheat", 1.5);
        config.biomeGrowth.put("minecraft:plains", plainsGrowth);

        // Configure Desert
        BiomeGrowthConfig desertGrowth = new BiomeGrowthConfig(0.2);
        desertGrowth.addCrop("minecraft:cactus", 2.5);
        config.biomeGrowth.put("minecraft:desert", desertGrowth);

        // Configure Badlands Ores
        BiomeWorldgenConfig badlandsWgen = new BiomeWorldgenConfig();
        badlandsWgen.oreMultipliers.put("gold", 3.5);
        config.biomeWorldgen.put("minecraft:badlands", badlandsWgen);

        // Configure Mob Spawns
        BiomeMobConfig plainsMobs = new BiomeMobConfig(0.8, 2.0);
        config.biomeMobs.put("minecraft:plains", plainsMobs);

        // Animal Growth & Fishing
        config.biomeAnimalGrowth.put("minecraft:plains", 1.8);
        config.biomeFishing.put("minecraft:ocean", 1.4);
        config.biomeFishing.put("minecraft:desert", 0.2);
    }

    @Test
    public void testTerraBiomeArchetypeResolution() {
        ResourceLocation terraTundra = ResourceLocation.fromNamespaceAndPath("terra", "overworld/overworld/tundra_hills");
        assertEquals("minecraft:snowy_plains", RegionalMultiplierService.resolveBiomeKey(terraTundra, config));

        ResourceLocation terraRainforest = ResourceLocation.fromNamespaceAndPath("terra", "overworld/overworld/rainforest");
        assertEquals("minecraft:jungle", RegionalMultiplierService.resolveBiomeKey(terraRainforest, config));

        ResourceLocation terraArid = ResourceLocation.fromNamespaceAndPath("terra", "overworld/overworld/arid_plateau");
        assertEquals("minecraft:desert", RegionalMultiplierService.resolveBiomeKey(terraArid, config));

        ResourceLocation terraSteppe = ResourceLocation.fromNamespaceAndPath("terra", "overworld/overworld/steppe");
        assertEquals("minecraft:plains", RegionalMultiplierService.resolveBiomeKey(terraSteppe, config));
    }

    @Test
    public void testCropMultiplierCalculation() {
        ResourceLocation plainsBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        ResourceLocation wheat = ResourceLocation.fromNamespaceAndPath("minecraft", "wheat");
        ResourceLocation carrot = ResourceLocation.fromNamespaceAndPath("minecraft", "carrots");

        // Specific crop bonus
        double wheatMult = RegionalMultiplierService.computeCropMultiplier(plainsBiome, wheat, new BlockPos(0, 70, 0), false, config);
        assertEquals(1.5, wheatMult, 0.0001);

        // Biome default
        double carrotMult = RegionalMultiplierService.computeCropMultiplier(plainsBiome, carrot, new BlockPos(0, 70, 0), false, config);
        assertEquals(1.2, carrotMult, 0.0001);

        // Mountain altitude debuff
        double mountainWheat = RegionalMultiplierService.computeCropMultiplier(plainsBiome, wheat, new BlockPos(0, 100, 0), false, config);
        assertEquals(1.5 * 0.5, mountainWheat, 0.0001);

        // Underground block
        double undergroundWheat = RegionalMultiplierService.computeCropMultiplier(plainsBiome, wheat, new BlockPos(0, 30, 0), true, config);
        assertEquals(0.0, undergroundWheat, 0.0001);
    }

    @Test
    public void testOreMultiplierCalculation() {
        ResourceLocation badlandsBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "badlands");
        ResourceLocation goldOre = ResourceLocation.fromNamespaceAndPath("minecraft", "gold_ore");
        ResourceLocation ironOre = ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ore");

        double goldMult = RegionalMultiplierService.computeOreMultiplier(badlandsBiome, goldOre, "gold", config);
        assertEquals(3.5, goldMult, 0.0001);

        // Global fallback
        config.globalOreMultipliers.put("diamond", 0.10);
        ResourceLocation diamondOre = ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_ore");
        double diamondMult = RegionalMultiplierService.computeOreMultiplier(badlandsBiome, diamondOre, "diamond", config);
        assertEquals(0.10, diamondMult, 0.0001);

        // Default 1.0
        double ironMult = RegionalMultiplierService.computeOreMultiplier(badlandsBiome, ironOre, "iron", config);
        assertEquals(1.0, ironMult, 0.0001);
    }

    @Test
    public void testMobMultiplierCalculation() {
        ResourceLocation plainsBiome = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        ResourceLocation cow = ResourceLocation.fromNamespaceAndPath("minecraft", "cow");
        ResourceLocation zombie = ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");

        double cowMult = RegionalMultiplierService.computeMobSpawnMultiplier(plainsBiome, MobCategory.CREATURE, cow, config);
        assertEquals(2.0, cowMult, 0.0001);

        double zombieMult = RegionalMultiplierService.computeMobSpawnMultiplier(plainsBiome, MobCategory.MONSTER, zombie, config);
        assertEquals(0.8, zombieMult, 0.0001);
    }

    @Test
    public void testAnimalGrowthAndFishingMultipliers() {
        ResourceLocation plains = ResourceLocation.fromNamespaceAndPath("minecraft", "plains");
        ResourceLocation ocean = ResourceLocation.fromNamespaceAndPath("minecraft", "ocean");
        ResourceLocation desert = ResourceLocation.fromNamespaceAndPath("minecraft", "desert");

        assertEquals(1.8, RegionalMultiplierService.computeAnimalGrowthMultiplier(plains, config), 0.0001);
        assertEquals(1.4, RegionalMultiplierService.computeFishingMultiplier(ocean, config), 0.0001);
        assertEquals(0.2, RegionalMultiplierService.computeFishingMultiplier(desert, config), 0.0001);
    }
}
