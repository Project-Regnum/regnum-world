package zooller.regnumworld.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zooller.regnumworld.fishing.FishingHelper;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @Shadow private int timeUntilLured;

    @Inject(method = "tick", at = @At("TAIL"))
    private void regnumworld$onFishingTick(CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (!hook.level().isClientSide && this.timeUntilLured > 0) {
            this.timeUntilLured = FishingHelper.adjustWaitTime(hook, this.timeUntilLured, hook.level().getRandom());
        }
    }
}
