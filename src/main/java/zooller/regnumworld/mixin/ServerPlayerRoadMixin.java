package zooller.regnumworld.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zooller.regnumworld.road.RoadHelper;

@Mixin(ServerPlayer.class)
public class ServerPlayerRoadMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void regnumworld$onPlayerRoadTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        // Check every 4 ticks (5 times per second for smooth, responsive detection)
        if (player.tickCount % 4 == 0) {
            RoadHelper.handleRoadSpeed(player);
        }
    }
}
