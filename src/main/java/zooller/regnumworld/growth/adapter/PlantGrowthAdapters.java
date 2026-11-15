package zooller.regnumworld.growth.adapter;

import net.minecraft.world.level.block.*;

import java.util.HashMap;
import java.util.Map;

public final class PlantGrowthAdapters {
    private static final Map<Class<? extends Block>, PlantGrowthAdapter> ADAPTERS = new HashMap<>();
    private static final PlantGrowthAdapter CROP_ADAPTER = new CropGrowthAdapter();

    static {
        ADAPTERS.put(CactusBlock.class, new CactusGrowthAdapter());
        ADAPTERS.put(SugarCaneBlock.class, new SugarCaneGrowthAdapter());
        ADAPTERS.put(BambooStalkBlock.class, new BambooGrowthAdapter());
        ADAPTERS.put(StemBlock.class, new StemGrowthAdapter());
        ADAPTERS.put(SweetBerryBushBlock.class, new SweetBerryBushGrowthAdapter());
        ADAPTERS.put(CocoaBlock.class, new CocoaGrowthAdapter());
        ADAPTERS.put(NetherWartBlock.class, new NetherWartGrowthAdapter());
        ADAPTERS.put(SaplingBlock.class, new SaplingGrowthAdapter());
        ADAPTERS.put(CropBlock.class, CROP_ADAPTER);
    }

    private PlantGrowthAdapters() {}

    public static PlantGrowthAdapter getAdapter(Block block) {
        if (block == null) {
            return null;
        }

        PlantGrowthAdapter direct = ADAPTERS.get(block.getClass());
        if (direct != null) {
            return direct;
        }

        // Check instance hierarchy (e.g. custom CropBlock subclasses)
        if (block instanceof CropBlock) {
            return CROP_ADAPTER;
        }
        for (Map.Entry<Class<? extends Block>, PlantGrowthAdapter> entry : ADAPTERS.entrySet()) {
            if (entry.getKey().isInstance(block)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
