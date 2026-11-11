package zooller.regnumworld.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import zooller.regnumworld.config.RegnumConfig;

public class RoadHelper {
    public static final ResourceLocation ROAD_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("regnum-world", "road_speed");

    public static void handleRoadSpeed(ServerPlayer player) {
        RegnumConfig config = RegnumConfig.getInstance();
        LivingEntity vehicleMount = player.getVehicle() instanceof LivingEntity livingVehicle ? livingVehicle : null;
        Entity entityToInspect = vehicleMount != null ? vehicleMount : player;

        if (!config.roadsEnabled) {
            removeRoadModifier(player);
            if (vehicleMount != null) {
                removeRoadModifier(vehicleMount);
            }
            return;
        }

        Level level = player.level();

        // Check the block beneath the entity
        BlockPos onPos = entityToInspect.getOnPos();
        int tier = getRoadTier(level.getBlockState(onPos).getBlock());

        // Check feet position for paths/slabs
        if (tier == 0) {
            BlockPos feetPos = entityToInspect.blockPosition();
            tier = getRoadTier(level.getBlockState(feetPos).getBlock());
            if (tier == 0) {
                tier = getRoadTier(level.getBlockState(feetPos.below()).getBlock());
            }
        }

        // Stepped off the road -> remove modifier cleanly
        if (tier <= 0) {
            removeRoadModifier(player);
            if (vehicleMount != null) {
                removeRoadModifier(vehicleMount);
            }
            return;
        }

        // On the road -> apply subtle attribute modifier without any potion effects
        double bonus = tier >= 2 ? config.roadTier2SpeedBonus : config.roadTier1SpeedBonus;

        applyRoadModifier(player, bonus);
        if (vehicleMount != null) {
            applyRoadModifier(vehicleMount, bonus);
        }
    }

    private static void applyRoadModifier(LivingEntity entity, double bonus) {
        AttributeInstance attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }

        AttributeModifier existing = attr.getModifier(ROAD_SPEED_MODIFIER_ID);
        if (existing != null) {
            if (Math.abs(existing.amount() - bonus) < 0.0001) {
                return; // already has correct modifier
            }
            attr.removeModifier(ROAD_SPEED_MODIFIER_ID);
        }

        if (bonus > 0.0) {
            attr.addTransientModifier(new AttributeModifier(
                    ROAD_SPEED_MODIFIER_ID,
                    bonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }
    }

    public static void removeRoadModifier(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.hasModifier(ROAD_SPEED_MODIFIER_ID)) {
            attr.removeModifier(ROAD_SPEED_MODIFIER_ID);
        }
    }

    /**
     * Determines road tier:
     * 2 = Imperial Paved Highway (Stone Bricks, Deepslate Bricks, Smooth Stone, Bricks) -> subtle +15%
     * 1 = Rustic Country Trail (Dirt Path, Gravel, Cobblestone, Sandstone, Planks) -> subtle +8%
     * 0 = Not a road
     */
    public static int getRoadTier(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return 0;
        }

        String path = id.getPath();

        // Tier 2: Paved Imperial Highways
        if (path.contains("stone_brick") ||
                path.contains("deepslate_brick") ||
                path.contains("deepslate_tile") ||
                path.contains("polished_deepslate") ||
                path.contains("polished_blackstone") ||
                path.contains("smooth_stone") ||
                path.contains("mud_brick") ||
                path.contains("cut_sandstone") ||
                path.contains("smooth_sandstone") ||
                path.equals("bricks") ||
                path.contains("brick_slab") ||
                path.contains("brick_stairs")) {
            return 2;
        }

        // Tier 1: Rustic Country Roads & Dirt Paths
        if (path.equals("dirt_path") ||
                path.contains("gravel") ||
                path.contains("cobblestone") ||
                path.contains("sandstone") ||
                path.contains("planks")) {
            return 1;
        }

        return 0;
    }
}
