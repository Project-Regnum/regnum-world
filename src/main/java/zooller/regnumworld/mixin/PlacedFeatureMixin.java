package zooller.regnumworld.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.worldgen.WorldgenHelper;

@Mixin(PlacedFeature.class)
public class PlacedFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void regnumworld$onPlace(
            WorldGenLevel level,
            ChunkGenerator generator,
            RandomSource random,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        WorldgenHelper.handleFeaturePlacement((PlacedFeature)(Object)this, level, generator, random, pos, cir);
    }
}
