package zooller.regnumworld.dungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import java.util.*;
import zooller.regnumworld.dungeon.DungeonData.Dungeon;

final class DungeonAdmin {
    record Confirmation(UUID target,String token,long expires,Runnable action) {
        boolean accepts(String supplied,long now){return supplied!=null&&token.equals(supplied)&&now<expires;}
    }
    private static final Map<String,Confirmation> confirmations=new HashMap<>();
    private static String actor(CommandSourceStack s){return s.getEntity()==null?s.getTextName():s.getEntity().getStringUUID();}
    static void clear(){confirmations.clear();}
    static void confirm(CommandSourceStack source,String message,Runnable action){confirm(source,null,message,action);}
    private static void confirm(CommandSourceStack source,UUID target,String message,Runnable action) {
        String token=UUID.randomUUID().toString().substring(0,6);
        confirmations.put(actor(source),new Confirmation(target,token,System.currentTimeMillis()+60000,action));
        source.sendSuccess(()->Component.literal(message+" ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("[CONFIRM]").withStyle(style->style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/regnum dungeon confirm "+token))))
            .append(" ").append(Component.literal("[CANCEL]").withStyle(style->style.withColor(ChatFormatting.GRAY).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/regnum dungeon cancel")))),false);
    }
    private static int executeConfirmation(CommandSourceStack source,String token) {
        var pending=confirmations.get(actor(source));
        if(pending==null||!pending.accepts(token,System.currentTimeMillis())) {
            source.sendFailure(Component.literal("Confirmation expired or superseded. Repeat the original command."));return 0;
        }
        confirmations.remove(actor(source));pending.action().run();return 1;
    }
    static String shortId(Dungeon d){return d.id.toString().substring(0,8);}
    private static Dungeon resolve(CommandSourceStack source,String id) {
        var matches=DungeonData.get(source.getServer()).entries.values().stream().filter(d->d.id.toString().startsWith(id)).toList();
        if(matches.size()!=1){source.sendFailure(Component.literal(matches.isEmpty()?"Unknown dungeon ID.":"Ambiguous ID; use more characters."));return null;}return matches.getFirst();
    }
    static void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("help").executes(c->{c.getSource().sendSuccess(()->Component.literal("/regnum dungeon generate <type> [here|force|x z] · list · info <id> · tp <id> · remove <id> · reset <id> · recover <id> · confirm <token> · cancel · preview/validate <type>. Removal keeps terrain; earned pending rewards are retained."),false);return 1;}));
        root.then(Commands.literal("list").executes(c->{var all=DungeonData.get(c.getSource().getServer()).entries.values();if(all.isEmpty())c.getSource().sendSuccess(()->Component.literal("No registered dungeons."),false);for(Dungeon d:all)c.getSource().sendSuccess(()->Component.literal(shortId(d)+" · "+d.name()+" · T"+d.tier()+" · "+d.dimension+" · "+d.x+", "+d.z),false);return all.size();}));
        root.then(Commands.literal("confirm").executes(c->executeConfirmation(c.getSource(),null))
            .then(Commands.argument("token",StringArgumentType.word()).executes(c->executeConfirmation(c.getSource(),StringArgumentType.getString(c,"token")))));
        root.then(Commands.literal("cancel").executes(c->{confirmations.remove(actor(c.getSource()));return 1;}));
        for(String action:List.of("info","tp","remove","reset","recover"))root.then(Commands.literal(action).then(Commands.argument("id",StringArgumentType.word())
            .suggests((c,b)->SharedSuggestionProvider.suggest(DungeonData.get(c.getSource().getServer()).entries.values().stream().map(DungeonAdmin::shortId),b))
            .executes(c->{var source=c.getSource();Dungeon d=resolve(source,StringArgumentType.getString(c,"id"));if(d==null)return 0;
                if(action.equals("info")){source.sendSuccess(()->Component.literal(d.name()+" ["+d.id+"] · "+d.dimension+" · origin "+d.x+" "+d.y+" "+d.z+" · "+d.power()+" · "+d.state()+" · cooldown "+Math.max(0,(d.bossReady-System.currentTimeMillis())/1000)+"s · bounds Y "+d.minimumY+".."+d.maximumY+" · layout "+d.layoutVersion+" · rooms cleared "+Dungeons.clearedRooms(d)+"/8 · mobs "+d.mobs.size()+" · pending rewards "+d.pending.size()),false);return 1;}
                ServerLevel world=source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(d.dimension)));if(world==null)return 0;
                if(action.equals("tp")){ServerPlayer player=source.getPlayerOrException();world.getChunk((d.x+24)>>4,d.z>>4);player.teleportTo(world,d.x+24.5,d.frontY,d.z+0.5,0,0);return 1;}
                long expectedCycle=d.cycle;
                confirm(source,d.id,(action.equals("remove")?"Unregister ":action.equals("reset")?"Reset encounters and invalidate the current reward cycle in ":"Rebuild registered layout in ")+d.name()+" ["+shortId(d)+"]. "+(action.equals("remove")?"Physical blocks stay in place; no original terrain snapshot exists.":""),()->{
                    if(DungeonData.get(source.getServer()).entries.get(d.id)!=d||d.cycle!=expectedCycle){source.sendFailure(Component.literal("Dungeon no longer exists."));return;}
                    if(action.equals("recover")) {for(int x=d.x>>4;x<=(d.x+47)>>4;x++)for(int z=d.z>>4;z<=(d.z+47)>>4;z++)world.getChunk(x,z);d.layoutVersion=3;d.maximumY=Math.max(d.maximumY,Math.max(d.frontY,d.backY)+5);Dungeons.building=true;try{DungeonStructures.build(world,d);}finally{Dungeons.building=false;}}
                    else {Dungeons.clear(world,d);d.cycle++;Arrays.fill(d.started,false);Arrays.fill(d.cleared,false);Arrays.fill(d.roomReady,0);d.rewarded.clear();d.level=1;d.escalationAt=System.currentTimeMillis();d.bossDefeated=false;d.bossReady=0;
                        if(action.equals("remove")){DungeonData data=DungeonData.get(source.getServer());data.remove(d);if(!d.pending.isEmpty())data.retired.add(d);}
                    }
                    DungeonData.get(source.getServer()).setDirty();source.getServer().overworld().getDataStorage().save();Dungeons.atlasChanged(world);source.sendSuccess(()->Component.literal("Dungeon "+action+" complete."),true);
                });return 1;
            })));
    }
}
