package zooller.regnumworld.dungeon;

import java.util.*;

/** Register additional themes during mod initialization; loot tables are normal reloadable data-pack assets. */
public final class DungeonTypes {
    public record Definition(String id,String name,int tier,int partySize,String boss,String mob,String lootTable) {
        public Definition {
            if(!id.matches("[a-z0-9_]+")||name.isBlank()||tier<1||tier>3||partySize<1)throw new IllegalArgumentException("Invalid dungeon definition");
        }
    }
    private static final Map<String,Definition> TYPES=new LinkedHashMap<>();
    static {
        register(new Definition("mine","Forgotten Mine",1,2,"Foreman of the Forgotten","minecraft:zombie","regnum-world:dungeon/mine"));
        register(new Definition("catacombs","Ancient Catacombs",2,3,"The Crypt Warden","minecraft:skeleton","regnum-world:dungeon/catacombs"));
        register(new Definition("bastion","Ruined Bastion",3,4,"Bastion Castellan","minecraft:piglin_brute","regnum-world:dungeon/bastion"));
    }
    public static void register(Definition definition) {
        if(TYPES.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("Duplicate dungeon type: "+definition.id());
    }
    public static Definition get(String id) {return Objects.requireNonNull(TYPES.get(id),"Missing dungeon definition: "+id);}
    public static List<String> ids() {return List.copyOf(TYPES.keySet());}
    private DungeonTypes() {}
}
