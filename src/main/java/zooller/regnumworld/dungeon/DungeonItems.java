package zooller.regnumworld.dungeon;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import zooller.regnumworld.RegnumWorld;

public final class DungeonItems {
    public static final Item FORGOTTEN_IRON=register("forgotten_iron",Rarity.UNCOMMON);
    public static final Item ANCIENT_BONE=register("ancient_bone_fragments",Rarity.UNCOMMON);
    public static final Item RUINED_SIGIL=register("ruined_sigil",Rarity.RARE);
    public static final Item CORRUPTED_CRYSTAL=register("corrupted_crystal",Rarity.RARE);
    private static final Item MINE_TROPHY=register("foreman_trophy",Rarity.RARE),CRYPT_TROPHY=register("warden_trophy",Rarity.EPIC),BASTION_TROPHY=register("castellan_trophy",Rarity.EPIC);
    private static Item register(String name,Rarity rarity){return Registry.register(BuiltInRegistries.ITEM,RegnumWorld.id(name),new Item(new Item.Properties().rarity(rarity)) {
        @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flag) {
            lines.add(net.minecraft.network.chat.Component.translatable("item.regnum-world."+name+".hint").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });}
    public static void initialize(){
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries->{for(Item item:new Item[]{FORGOTTEN_IRON,ANCIENT_BONE,RUINED_SIGIL,CORRUPTED_CRYSTAL,MINE_TROPHY,CRYPT_TROPHY,BASTION_TROPHY})entries.accept(item);});
    }
    public static Item material(String type){return type.equals("mine")?FORGOTTEN_IRON:type.equals("catacombs")?ANCIENT_BONE:RUINED_SIGIL;}
    public static Item trophy(String type){return type.equals("mine")?MINE_TROPHY:type.equals("catacombs")?CRYPT_TROPHY:BASTION_TROPHY;}
}
