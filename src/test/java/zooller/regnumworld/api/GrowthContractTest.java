package zooller.regnumworld.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GrowthContractTest {
    @Test void delegationIsOrderedReplayIsScopedAndRecursionIsBlocked(){
        ExternalGrowthController controller=(state,level,pos,random,multiplier)->{
            assertEquals(GrowthResult.CANCEL,RegnumWorldApi.delegateCropTick(state,level,pos,random,multiplier));
            assertThrows(IllegalArgumentException.class,()->RegnumWorldApi.replayCropTick(()->{
                assertTrue(RegnumWorldApi.isReplayingCropTick());throw new IllegalArgumentException();
            }));
            assertFalse(RegnumWorldApi.isReplayingCropTick());return GrowthResult.HANDLED;
        };
        RegnumWorldApi.registerGrowthController(controller);
        try{
            assertThrows(UnsupportedOperationException.class,()->RegnumWorldApi.getControllers().clear());
            assertEquals(GrowthResult.HANDLED,RegnumWorldApi.delegateCropTick(null,null,null,null,2));
        }finally{RegnumWorldApi.unregisterGrowthController(controller);}
        assertEquals(GrowthResult.PASS,RegnumWorldApi.delegateCropTick(null,null,null,null,1));
        assertThrows(IllegalStateException.class,()->RegnumWorldApi.replayCropTick(()->{}));
    }
}
