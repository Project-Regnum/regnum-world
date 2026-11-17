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
    public static final int GROWTH_API_VERSION = 1;
    private static final ThreadLocal<Boolean> REPLAY = ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> DELEGATING = ThreadLocal.withInitial(()->false);

    /** Pure policy query; never invokes controllers or mutates crops. No authoritative season system exists. */
    public record CropGrowthPolicy(int version, boolean blocked, double multiplier) {}
    public static CropGrowthPolicy evaluateCropGrowth(ServerLevel level, BlockPos pos, BlockState state) {
        boolean blocked=isUndergroundGrowthBlocked(level,pos);
        return new CropGrowthPolicy(GROWTH_API_VERSION,blocked,blocked?0:getFinalCropMultiplier(level,pos,state));
    }
    /** Controller-owned vanilla replay: World rules are already accounted for. Always restored on exception. */
    public static void replayCropTick(Runnable tick) {
        if(!DELEGATING.get())throw new IllegalStateException("Replay requires an active growth controller callback");
        boolean previous=REPLAY.get();REPLAY.set(true);
        try{tick.run();}finally{REPLAY.set(previous);}
    }
    public static boolean isReplayingCropTick(){return REPLAY.get();}
    /** First HANDLED/CANCEL wins. Nested unmarked dispatch is cancelled to prevent recursion. */
    public static GrowthResult delegateCropTick(BlockState state, ServerLevel level, BlockPos pos,
            net.minecraft.util.RandomSource random, double multiplier) {
        if(DELEGATING.get())return GrowthResult.CANCEL;
        DELEGATING.set(true);
        try {
            for(var controller:CONTROLLERS){
                GrowthResult result=java.util.Objects.requireNonNull(controller.onRandomTick(state,level,pos,random,multiplier));
                if(result!=GrowthResult.PASS)return result;
            }
            return GrowthResult.PASS;
        }finally{DELEGATING.set(false);}
    }
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
     * Returns an immutable snapshot of registered controllers.
     */
    public static List<ExternalGrowthController> getControllers() {
        return List.copyOf(CONTROLLERS);
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
