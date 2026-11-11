package zooller.regnumworld.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zooller.regnumworld.config.RegnumConfig;

public class MobSpawnHelper {
    private static final ThreadLocal<Boolean> IS_SPAWNING_EXTRA = ThreadLocal.withInitial(() -> false);

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

        double multiplier = RegnumConfig.getInstance().getMobSpawnMultiplier(biomeId, category, mobId);

        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return;
        }

        if (multiplier <= 0.0) {
            cir.setReturnValue(false);
            return;
        }

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

        BlockPos pos = mob.blockPosition();
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);
        if (biomeId == null) {
            return;
        }

        MobCategory category = mob.getType().getCategory();
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());

        double multiplier = RegnumConfig.getInstance().getMobSpawnMultiplier(biomeId, category, mobId);

        if (multiplier <= 1.0) {
            return;
        }

        double extra = multiplier - 1.0;
        int guaranteedExtra = (int) extra;
        double fractional = extra - guaranteedExtra;

        RandomSource random = level.getRandom();
        int extraCount = guaranteedExtra + (fractional > 0 && random.nextDouble() < fractional ? 1 : 0);

        if (extraCount > 0 && level instanceof ServerLevel serverLevel) {
            IS_SPAWNING_EXTRA.set(true);
            try {
                for (int i = 0; i < extraCount; i++) {
                    Entity copy = mob.getType().create(serverLevel);
                    if (copy instanceof Mob extraMob) {
                        double dx = (random.nextDouble() - 0.5) * 4.0;
                        double dz = (random.nextDouble() - 0.5) * 4.0;
                        extraMob.moveTo(mob.getX() + dx, mob.getY(), mob.getZ() + dz, mob.getYRot(), mob.getXRot());
                        serverLevel.addFreshEntity(extraMob);
                    }
                }
            } finally {
                IS_SPAWNING_EXTRA.set(false);
            }
        }
    }
}
