package zooller.regnumworld.config;

import java.util.HashMap;
import java.util.Map;

public class BiomeWorldgenConfig {
    /**
     * Tree placement frequency multiplier in this biome.
     * 1.0 = normal vanilla, 0.0 = no trees, 2.0 = double trees, 0.5 = half trees.
     */
    public double treeMultiplier = 1.0;

    /**
     * Flower placement frequency multiplier in this biome.
     * 1.0 = normal vanilla, 0.0 = no flowers, 2.0 = double flowers, 0.2 = rare flowers.
     */
    public double flowerMultiplier = 1.0;

    /**
     * Grass, tall grass and fern placement frequency multiplier in this biome.
     * 1.0 = normal vanilla, 0.0 = completely barren of grass, 0.1 = sparse grass.
     */
    public double grassMultiplier = 1.0;

    /**
     * Specific vegetation multipliers, e.g. "dead_bush" -> 2.0, "cactus" -> 2.0, "sugar_cane" -> 1.5,
     * "pumpkin" -> 1.5, "melon" -> 1.5, "mushroom" -> 2.0, "sunflower" -> 2.0.
     */
    public Map<String, Double> vegetationMultipliers = new HashMap<>();

    /**
     * Ore placement frequency multipliers in this biome.
     * Keys can be ore names ("coal", "iron", "copper", "gold", "redstone", "diamond", "lapis", "emerald", "quartz", "debris")
     * or block IDs ("minecraft:diamond_ore", "minecraft:deepslate_iron_ore").
     */
    public Map<String, Double> oreMultipliers = new HashMap<>();

    public BiomeWorldgenConfig() {
    }

    public BiomeWorldgenConfig(double treeMultiplier) {
        this.treeMultiplier = treeMultiplier;
    }

    public BiomeWorldgenConfig(double treeMultiplier, double flowerMultiplier, double grassMultiplier) {
        this.treeMultiplier = treeMultiplier;
        this.flowerMultiplier = flowerMultiplier;
        this.grassMultiplier = grassMultiplier;
    }

    public BiomeWorldgenConfig addOre(String ore, double multiplier) {
        oreMultipliers.put(ore.toLowerCase(), multiplier);
        return this;
    }

    public BiomeWorldgenConfig addVegetation(String type, double multiplier) {
        vegetationMultipliers.put(type.toLowerCase(), multiplier);
        return this;
    }
}
