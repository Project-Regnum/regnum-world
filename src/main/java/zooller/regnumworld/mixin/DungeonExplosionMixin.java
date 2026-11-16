package zooller.regnumworld.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zooller.regnumworld.dungeon.Dungeons;

@Mixin(Explosion.class)
public abstract class DungeonExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow public abstract List<BlockPos> getToBlow();
    @Inject(method="finalizeExplosion",at=@At("HEAD"))
    private void regnum$dungeonExplosion(boolean particles,CallbackInfo ci) {
        getToBlow().removeIf(pos->Dungeons.protectedAt(level,pos));
    }
}
