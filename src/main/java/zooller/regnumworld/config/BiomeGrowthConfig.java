package zooller.regnumworld.config;

import java.util.HashMap;
import java.util.Map;

public class BiomeGrowthConfig {
    /**
     * Default growth multiplier for crops in this biome if not specifically defined in `crops`.
     * 1.0 = normal, 0.0 = crops do not grow, 2.0 = 2x faster, 0.5 = 2x slower.
     */
    public double defaultMultiplier = 1.0;

    /**
     * Specific crop growth multipliers, e.g. "minecraft:wheat" -> 1.5, "minecraft:cactus" -> 3.0.
     */
    public Map<String, Double> crops = new HashMap<>();

    public BiomeGrowthConfig() {
    }

    public BiomeGrowthConfig(double defaultMultiplier) {
        this.defaultMultiplier = defaultMultiplier;
    }

    public BiomeGrowthConfig addCrop(String blockId, double multiplier) {
        crops.put(blockId, multiplier);
        return this;
    }
}
