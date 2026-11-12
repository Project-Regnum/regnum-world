package zooller.regnumworld.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class RegnumWorldApiTest {

    @Test
    public void testExternalGrowthControllerRegistration() {
        AtomicBoolean called = new AtomicBoolean(false);
        ExternalGrowthController controller = (state, level, pos, random, multiplier) -> {
            called.set(true);
            return GrowthResult.HANDLED;
        };

        RegnumWorldApi.registerGrowthController(controller);
        assertTrue(RegnumWorldApi.hasActiveController());
        assertTrue(RegnumWorldApi.getControllers().contains(controller));

        // Invoke controller
        GrowthResult result = controller.onRandomTick(null, null, null, null, 1.5);
        assertEquals(GrowthResult.HANDLED, result);
        assertTrue(called.get());

        // Unregister
        RegnumWorldApi.unregisterGrowthController(controller);
        assertFalse(RegnumWorldApi.getControllers().contains(controller));
    }
}
