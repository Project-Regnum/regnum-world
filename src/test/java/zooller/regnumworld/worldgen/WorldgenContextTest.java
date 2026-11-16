package zooller.regnumworld.worldgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static zooller.regnumworld.worldgen.WorldgenTypeResolver.FeatureCategory.*;

class WorldgenContextTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlacedFeature patch(BlockStateProvider provider) {
        var inner = new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.SIMPLE_BLOCK,
                new SimpleBlockConfiguration(provider))), List.of());
        return new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.RANDOM_PATCH,
                new RandomPatchConfiguration(32, 7, 3, Holder.direct(inner)))), List.of());
    }

    @Test void positionDependentProviderIsSafeDuringParallelClassification() {
        var feature = patch(new NoiseProvider(42, new NormalNoise.NoiseParameters(0, 1.0),
                0.1f, List.of(Blocks.SHORT_GRASS.defaultBlockState())));
        // This is the original failure: a noise provider dereferences its position.
        assertThrows(NullPointerException.class, () ->
                ((SimpleBlockConfiguration) ((RandomPatchConfiguration) feature.feature().value().config())
                        .feature().value().feature().value().config()).toPlace().getState(null, null));
        IntStream.range(0, 256).parallel().forEach(i -> {
            var result = WorldgenTypeResolver.classify(feature, new BlockPos(-i, -60 + i, i));
            assertEquals(GRASS, result.category);
        });
    }

    @Test void randomDependentProviderGetsLocalRandomAndNullContextFallsBack() {
        var feature = patch(new WeightedStateProvider(SimpleWeightedRandomList
                .<net.minecraft.world.level.block.state.BlockState>builder()
                .add(Blocks.DEAD_BUSH.defaultBlockState(), 1)));
        assertEquals(DEAD_BUSH, WorldgenTypeResolver.classify(feature, BlockPos.ZERO).category);
        assertEquals(OTHER, WorldgenTypeResolver.classify(feature).category);
        assertEquals(OTHER, WorldgenTypeResolver.classify(null, BlockPos.ZERO).category);
        assertEquals(OTHER, WorldgenTypeResolver.classify(new PlacedFeature(null, List.of()), BlockPos.ZERO).category);
        var incomplete = new PlacedFeature(Holder.direct(new ConfiguredFeature<>(Feature.RANDOM_PATCH,
                new RandomPatchConfiguration(1, 1, 1,
                        Holder.direct(new PlacedFeature(null, List.of()))))), List.of());
        assertEquals(OTHER, WorldgenTypeResolver.classify(incomplete, BlockPos.ZERO).category);
    }
}
