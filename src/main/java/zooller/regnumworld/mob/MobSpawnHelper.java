package zooller.regnumworld.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.config.RegnumConfig;
import zooller.regnumworld.service.RegionalMultiplierService;

public class MobSpawnHelper {
    private static final ThreadLocal<Boolean> IS_SPAWNING_EXTRA = ThreadLocal.withInitial(() -> false);
    private static final int MAX_EXTRA_MOBS_PER_EVENT = 2;

    public static void handleCheckSpawnRules(Mob mob, LevelAccessor level, MobSpawnType spawnType, CallbackInfoReturnable<Boolean> cir) {
        if (IS_SPAWNING_EXTRA.get()) {
            return;
        }

        // Only regulate natural spawning and chunk generation spawns
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return;
        }

        BlockPos pos = mob.blockPosition();
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return;
        }

        MobCategory category = mob.getType().getCategory();
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());

        double multiplier = RegionalMultiplierService.computeMobSpawnMultiplier(
                biomeId,
                category,
                mobId,
                RegnumConfig.getInstance()
        );

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }

        if (multiplier <= 0.0) {
            cir.setReturnValue(false);
            return;
        }

        // Less than 1.0: cancel natural spawn probabilistically
        if (multiplier < 1.0) {
            if (level.getRandom().nextDouble() >= multiplier) {
                cir.setReturnValue(false);
            }
        }
    }

    public static void handleFinalizeSpawn(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType) {
        if (IS_SPAWNING_EXTRA.get()) {
            return;
        }

        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = mob.blockPosition();
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return;
        }

        MobCategory category = mob.getType().getCategory();
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());

        double multiplier = RegionalMultiplierService.computeMobSpawnMultiplier(
                biomeId,
                category,
                mobId,
                RegnumConfig.getInstance()
        );

        if (multiplier <= 1.0) {
            return;
        }

        double extra = multiplier - 1.0;
        int guaranteedExtra = (int) extra;
        double fractional = extra - guaranteedExtra;

        RandomSource random = level.getRandom();
        int requestedExtra = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);
        int extraCount = Math.min(MAX_EXTRA_MOBS_PER_EVENT, requestedExtra);

        if (extraCount <= 0) {
            return;
        }

        // Density check to avoid overflowing mob caps
        int nearbySimilar = serverLevel.getEntitiesOfClass(
                mob.getClass(),
                mob.getBoundingBox().inflate(16.0),
                Entity::isAlive
        ).size();

        int maxNearby = category == MobCategory.CREATURE ? 10 : 20;
        if (nearbySimilar >= maxNearby) {
            return;
        }

        IS_SPAWNING_EXTRA.set(true);
        try {
            for (int i = 0; i < extraCount; i++) {
                double offsetX = (random.nextDouble() - 0.5) * 6.0;
                double offsetZ = (random.nextDouble() - 0.5) * 6.0;
                BlockPos targetPos = BlockPos.containing(mob.getX() + offsetX, mob.getY(), mob.getZ() + offsetZ);

                // Find valid standing surface
                if (!level.getBlockState(targetPos).isAir()) {
                    targetPos = targetPos.above();
                }

                // Verify natural spawn placement rules for this entity type
                if (!SpawnPlacements.checkSpawnRules(mob.getType(), serverLevel, spawnType, targetPos, random)) {
                    continue;
                }

                Entity created = mob.getType().create(serverLevel);
                if (created instanceof Mob extraMob) {
                    extraMob.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);

                    if (!extraMob.checkSpawnObstruction(serverLevel)) {
                        extraMob.discard();
                        continue;
                    }

                    // Complete vanilla spawn initialization (equipment, variants, baby chance, health)
                    extraMob.finalizeSpawn(
                            serverLevel,
                            serverLevel.getCurrentDifficultyAt(targetPos),
                            spawnType,
                            null
                    );

                    serverLevel.addFreshEntity(extraMob);
                }
            }
        } finally {
            IS_SPAWNING_EXTRA.set(false);
        }
    }
}
