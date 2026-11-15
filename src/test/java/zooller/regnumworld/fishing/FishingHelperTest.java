package zooller.regnumworld.fishing;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FishingHelperTest {

    @Test
    public void testFishingCountdownMath() {
        // Multiplier 2.0: wait time countdown runs 2x faster
        double multiplier = 2.0;
        double extra = multiplier - 1.0;
        int guaranteedExtra = (int) extra;
        assertEquals(1, guaranteedExtra);

        // At initial lured time 300:
        // Decrement per tick is 1 (vanilla) + 1 (extra) = 2 ticks per game tick.
        // Total ticks to complete = 300 / 2 = 150 (2x faster!)
        int startWait = 300;
        int ticks = 0;
        int current = startWait;
        while (current > 1) {
            // vanilla decrement
            current--;
            // regnum adjustment
            current = Math.max(1, current - guaranteedExtra);
            ticks++;
        }
        assertEquals(150, ticks);
    }
}
