package zooller.regnumworld.mixin;

import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zooller.regnumworld.mob.AnimalGrowthHelper;

@Mixin(AgeableMob.class)
public class AgeableMobMixin {
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void regnumworld$onAnimalAiStep(CallbackInfo ci) {
        AgeableMob mob = (AgeableMob) (Object) this;
        if (!mob.level().isClientSide && mob.isAlive()) {
            AnimalGrowthHelper.handleAnimalTick(mob);
        }
    }
}
