package zooller.regnumworld.config;

import java.util.HashMap;
import java.util.Map;

public class BiomeMobConfig {
    /**
     * Default mob spawn multiplier for this biome.
     * 1.0 = normal vanilla rate, 0.0 = no mobs spawn, 2.0 = double mob spawns, 0.5 = half mob spawns.
     */
    public double defaultMultiplier = 1.0;

    /**
     * Multipliers for mob categories:
     * - "hostile" / "monster"
     * - "creature" / "passive" (cows, pigs, sheep, chickens)
     * - "water" (fish, squid, dolphins)
     * - "ambient" (bats)
     */
    public double hostileMultiplier = 1.0;
    public double creatureMultiplier = 1.0;
    public double waterMultiplier = 1.0;
    public double ambientMultiplier = 1.0;

    /**
     * Specific mob multipliers, e.g. "minecraft:husk" -> 2.5, "minecraft:creeper" -> 0.5.
     */
    public Map<String, Double> mobs = new HashMap<>();

    public BiomeMobConfig() {
    }

    public BiomeMobConfig(double hostileMultiplier, double creatureMultiplier) {
        this.hostileMultiplier = hostileMultiplier;
        this.creatureMultiplier = creatureMultiplier;
    }

    public BiomeMobConfig addMob(String mobId, double multiplier) {
        mobs.put(mobId.toLowerCase(), multiplier);
        return this;
    }
}
