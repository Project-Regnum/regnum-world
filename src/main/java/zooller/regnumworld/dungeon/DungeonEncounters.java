package zooller.regnumworld.dungeon;

/** Server-thread recovery rules; absence is never evidence of a kill. */
final class DungeonEncounters {
    static void missing(DungeonData.Dungeon d,int room,long now,long cooldown) {
        d.started[room]=d.mobs.containsValue(room);
        d.roomReady[room]=now+cooldown;
        if(room==8)d.bossReady=now+cooldown;
    }
    static void spawned(DungeonData.Dungeon d,int room,long now,long cooldown) {
        d.started[room]=d.mobs.containsValue(room);
        if(!d.started[room])d.roomReady[room]=now+cooldown;
    }
}
