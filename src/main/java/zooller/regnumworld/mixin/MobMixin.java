package zooller.regnumworld.mixin;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.mob.MobSpawnHelper;

@Mixin(Mob.class)
public class MobMixin {
    @Inject(method = "checkSpawnRules", at = @At("RETURN"), cancellable = true)
    private void regnumworld$onCheckSpawnRules(LevelAccessor level, MobSpawnType spawnType, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        Mob mob = (Mob) (Object) this;
        MobSpawnHelper.handleCheckSpawnRules(mob, level, spawnType, cir);
    }

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void regnumworld$onFinalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            SpawnGroupData spawnGroupData,
            CallbackInfoReturnable<SpawnGroupData> cir
    ) {
        Mob mob = (Mob) (Object) this;
        MobSpawnHelper.handleFinalizeSpawn(mob, level, spawnType);
    }
}
