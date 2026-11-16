package zooller.regnumworld.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.dungeon.Dungeons;

@Mixin(ServerPlayer.class)
public abstract class DungeonPlayerMixin {
    @Inject(method="canHarmPlayer",at=@At("HEAD"),cancellable=true)
    private void regnum$dungeonCombat(Player target,CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self=(ServerPlayer)(Object)this;
        if(self.level()==target.level() && Dungeons.at(target.level(),target.blockPosition())!=null)cir.setReturnValue(true);
    }
    @Inject(method="isPvpAllowed",at=@At("HEAD"),cancellable=true)
    private void regnum$dungeonPvp(CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self=(ServerPlayer)(Object)this;
        if(Dungeons.at(self.level(),self.blockPosition())!=null)cir.setReturnValue(true);
    }
}
