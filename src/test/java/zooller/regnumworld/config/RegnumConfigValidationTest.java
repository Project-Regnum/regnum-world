package zooller.regnumworld.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RegnumConfigValidationTest {

    @Test
    public void testSanitizeInvalidValues() {
        RegnumConfig config = new RegnumConfig();
        config.bonemealEfficiency = -0.5;
        config.mountainAltitudeMultiplier = 999.0;
        config.undergroundMaxY = 9999;

        config.globalOreMultipliers.put("broken", Double.NaN);
        config.globalOreMultipliers.put("negative", -10.0);
        config.globalOreMultipliers.put("huge", 500.0);
        config.globalOreMultipliers.put("   ", 1.5); // blank key

        RegnumConfig.validateConfig(config);

        // Negative efficiency clamped to min 0.0
        assertEquals(0.0, config.bonemealEfficiency, 0.0001);
        // Altitude clamped to max 10.0
        assertEquals(10.0, config.mountainAltitudeMultiplier, 0.0001);
        // Underground clamped back to 62
        assertEquals(62, config.undergroundMaxY);

        // Map values
        assertEquals(1.0, config.globalOreMultipliers.get("broken"), 0.0001);
        assertEquals(0.0, config.globalOreMultipliers.get("negative"), 0.0001);
        assertEquals(100.0, config.globalOreMultipliers.get("huge"), 0.0001);
        assertFalse(config.globalOreMultipliers.containsKey("   "));
    }

    @Test
    public void testNullMapsAreInitialized() {
        RegnumConfig config = new RegnumConfig();
        config.biomeGrowth = null;
        config.biomeWorldgen = null;
        config.biomeMobs = null;
        config.biomeFishing = null;
        config.biomeAnimalGrowth = null;

        RegnumConfig.validateConfig(config);

        assertNotNull(config.biomeGrowth);
        assertNotNull(config.biomeWorldgen);
        assertNotNull(config.biomeMobs);
        assertNotNull(config.biomeFishing);
        assertNotNull(config.biomeAnimalGrowth);
    }
}
