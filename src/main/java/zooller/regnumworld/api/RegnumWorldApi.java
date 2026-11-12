package zooller.regnumworld.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import zooller.regnumworld.config.RegnumConfig;
import zooller.regnumworld.service.RegionalMultiplierService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RegnumWorldApi {
    private static final List<ExternalGrowthController> CONTROLLERS = new CopyOnWriteArrayList<>();

    private RegnumWorldApi() {}

    /**
     * Registers an external growth controller (e.g. from Regnum Agriculture).
     */
    public static void registerGrowthController(ExternalGrowthController controller) {
        if (controller != null && !CONTROLLERS.contains(controller)) {
            CONTROLLERS.add(controller);
        }
    }

    /**
     * Unregisters an external growth controller.
     */
    public static void unregisterGrowthController(ExternalGrowthController controller) {
        CONTROLLERS.remove(controller);
    }

    /**
     * Returns the list of registered controllers.
     */
    public static List<ExternalGrowthController> getControllers() {
        return CONTROLLERS;
    }

    /**
     * Returns true if at least one external controller is registered.
     */
    public static boolean hasActiveController() {
        return !CONTROLLERS.isEmpty();
    }

    /**
     * Checks if underground crop growth is blocked at the given position.
     */
    public static boolean isUndergroundGrowthBlocked(Level level, BlockPos pos) {
        RegnumConfig config = RegnumConfig.getInstance();
        if (!config.blockUndergroundCropGrowth) {
            return false;
        }
        return pos.getY() <= config.undergroundMaxY ||
                (config.blockCaveCropGrowthWithoutSky && !level.canSeeSky(pos));
    }

    /**
     * Computes the base regional multiplier for a crop in a given biome.
     */
    public static double getRegionalCropMultiplier(ResourceLocation biomeId, ResourceLocation blockId) {
        RegnumConfig config = RegnumConfig.getInstance();
        return RegionalMultiplierService.computeCropMultiplier(biomeId, blockId, null, false, config);
    }

    /**
     * Computes the full regional crop multiplier for a block state at a position.
     */
    public static double getFinalCropMultiplier(ServerLevel level, BlockPos pos, BlockState state) {
        RegnumConfig config = RegnumConfig.getInstance();
        boolean isUnderground = isUndergroundGrowthBlocked(level, pos);
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return RegionalMultiplierService.computeCropMultiplier(biomeId, blockId, pos, isUnderground, config);
    }
}
