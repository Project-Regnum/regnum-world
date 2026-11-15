package zooller.regnumworld.growth.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import zooller.regnumworld.mixin.CropBlockInvoker;
import zooller.regnumworld.mixin.StemBlockAccessor;

import java.util.Optional;

public class StemGrowthAdapter implements PlantGrowthAdapter {
    @Override
    public boolean handleGrowth(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random, double multiplier) {
        if (multiplier <= 0.0) {
            return true;
        }

        if (multiplier < 1.0) {
            if (random.nextDouble() >= multiplier) {
                return true;
            }
            return false;
        }

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return false;
        }

        if (!(block instanceof StemBlock stemBlock)) {
            return false;
        }

        if (level.getRawBrightness(pos, 0) < 9) {
            return true;
        }

        float growthSpeed = CropBlockInvoker.invokeGetGrowthSpeed(stemBlock, level, pos);
        int denominator = (int) (25.0F / growthSpeed) + 1;
        double baseChance = 1.0 / denominator;

        double expectedSuccesses = multiplier * baseChance;
        int guaranteed = (int) expectedSuccesses;
        double fractional = expectedSuccesses - guaranteed;
        int rolls = guaranteed + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);

        if (rolls <= 0) {
            return true;
        }

        int age = state.getValue(StemBlock.AGE);
        if (age < 7) {
            int newAge = Math.min(7, age + rolls);
            level.setBlock(pos, state.setValue(StemBlock.AGE, newAge), 2);
        } else {
            Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos fruitPos = pos.relative(dir);
            BlockState soilState = level.getBlockState(fruitPos.below());

            if (level.isEmptyBlock(fruitPos) && (soilState.is(Blocks.FARMLAND) || soilState.is(BlockTags.DIRT))) {
                StemBlockAccessor accessor = (StemBlockAccessor) stemBlock;
                ResourceKey<Block> fruitKey = accessor.getFruit();
                Optional<Holder.Reference<Block>> fruitHolder = level.registryAccess().registryOrThrow(Registries.BLOCK).getHolder(fruitKey);

                fruitHolder.ifPresent(h -> {
                    level.setBlockAndUpdate(fruitPos, h.value().defaultBlockState());
                    ResourceKey<Block> attachedKey = accessor.getAttachedStem();
                    level.registryAccess().registryOrThrow(Registries.BLOCK).getHolder(attachedKey)
                            .ifPresent(att -> level.setBlockAndUpdate(
                                    pos,
                                    att.value().defaultBlockState().setValue(AttachedStemBlock.FACING, dir)
                            ));
                });
            }
        }

        return true;
    }
}
