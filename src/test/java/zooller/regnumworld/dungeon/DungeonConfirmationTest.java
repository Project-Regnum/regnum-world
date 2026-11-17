package zooller.regnumworld.dungeon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DungeonConfirmationTest {
    @Test void tokenIsRequiredAndOldButtonsCannotApproveReplacement(){
        var previous=new DungeonAdmin.Confirmation(null,"old",100,()->{});
        var current=new DungeonAdmin.Confirmation(null,"new",200,()->{});
        assertFalse(previous.accepts(null,50));assertFalse(previous.accepts("",50));
        assertTrue(previous.accepts("old",99));assertFalse(previous.accepts("old",100));
        assertFalse(current.accepts("old",50));assertTrue(current.accepts("new",50));
    }
}
