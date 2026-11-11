package zooller.regnumworld.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.growth.BoneMealHelper;

@Mixin(BoneMealItem.class)
public class BoneMealItemMixin {
    @Inject(method = "growCrop", at = @At("HEAD"), cancellable = true)
    private static void regnumworld$onGrowCrop(ItemStack stack, Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BoneMealHelper.handleBonemeal(level, pos, cir);
    }
}
