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
    @Shadow private int timeUntilHooked;
    @Shadow private int nibble;

    @Inject(method = "tick", at = @At("HEAD"))
    private void regnumworld$onFishingTickHead(CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (!hook.level().isClientSide) {
            double multiplier = FishingHelper.getMultiplier(hook);
            if (multiplier <= 0.0) {
                // Completely suppress bite when multiplier is 0
                if (this.timeUntilLured <= 5) {
                    this.timeUntilLured = 300;
                }
                if (this.timeUntilHooked > 0) {
                    this.timeUntilHooked = 0;
                }
                if (this.nibble > 0) {
                    this.nibble = 0;
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void regnumworld$onFishingTickTail(CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (!hook.level().isClientSide && this.timeUntilLured > 0) {
            this.timeUntilLured = FishingHelper.adjustWaitTime(hook, this.timeUntilLured, hook.level().getRandom());
        }
    }
}
