package zooller.regnumworld.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {
    @Unique
    private static final Logger regnumworld$LOGGER = LoggerFactory.getLogger("RegnumWorld-TerraCompat");

    @Unique
    private static final ResourceLocation regnumworld$TERRA_PRESET_ID = ResourceLocation.fromNamespaceAndPath("terra", "overworld/overworld");

    @Inject(
        method = "load(Lnet/minecraft/resources/RegistryDataLoader$LoadingFunction;Lnet/minecraft/core/RegistryAccess;Ljava/util/List;)Lnet/minecraft/core/RegistryAccess$Frozen;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
            ordinal = 1
        )
    )
    private static void regnumworld$onBeforeFreeze(
        CallbackInfoReturnable<?> cir,
        @Local(ordinal = 1) List<?> loaders
    ) {
        if (loaders == null || loaders.isEmpty()) {
            return;
        }

        WritableRegistry<Biome> biomeRegistry = null;
        WritableRegistry<WorldPreset> worldPresetRegistry = null;
        WritableRegistry<?> dimensionTypeRegistry = null;
        WritableRegistry<?> settingsRegistry = null;
        WritableRegistry<?> noiseRegistry = null;

        Method registryAccessor = null;
        for (Object loader : loaders) {
            if (loader == null) continue;
            try {
                if (registryAccessor == null) {
                    registryAccessor = loader.getClass().getMethod("registry");
                    registryAccessor.setAccessible(true);
                }
                Object regObj = registryAccessor.invoke(loader);
                if (regObj instanceof WritableRegistry<?> reg) {
                    ResourceKey<?> key = reg.key();
                    if (key.equals(Registries.BIOME)) {
                        //noinspection unchecked
                        biomeRegistry = (WritableRegistry<Biome>) reg;
                    } else if (key.equals(Registries.WORLD_PRESET)) {
                        //noinspection unchecked
                        worldPresetRegistry = (WritableRegistry<WorldPreset>) reg;
                    } else if (key.equals(Registries.DIMENSION_TYPE)) {
                        dimensionTypeRegistry = reg;
                    } else if (key.equals(Registries.NOISE_SETTINGS)) {
                        settingsRegistry = reg;
                    } else if (key.equals(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)) {
                        noiseRegistry = reg;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (biomeRegistry == null || worldPresetRegistry == null) {
            return;
        }

        ResourceKey<WorldPreset> terraPresetKey = ResourceKey.create(Registries.WORLD_PRESET, regnumworld$TERRA_PRESET_ID);
        if (worldPresetRegistry.containsKey(terraPresetKey)) {
            return;
        }

        try {
            Class<?> lifecyclePlatformClass = Class.forName("com.dfsek.terra.lifecycle.LifecyclePlatform");

            Field biomesField = lifecyclePlatformClass.getDeclaredField("BIOMES");
            biomesField.setAccessible(true);
            //noinspection unchecked
            AtomicReference<Registry<Biome>> biomesRef = (AtomicReference<Registry<Biome>>) biomesField.get(null);
            if (biomesRef != null) {
                biomesRef.set(biomeRegistry);
            }

            if (dimensionTypeRegistry != null) {
                Field dimField = lifecyclePlatformClass.getDeclaredField("DIMENSIONS");
                dimField.setAccessible(true);
                //noinspection unchecked
                AtomicReference<Object> dimRef = (AtomicReference<Object>) dimField.get(null);
                if (dimRef != null) {
                    dimRef.set(dimensionTypeRegistry);
                }
            }

            if (settingsRegistry != null) {
                Field setField = lifecyclePlatformClass.getDeclaredField("SETTINGS");
                setField.setAccessible(true);
                //noinspection unchecked
                AtomicReference<Object> setRef = (AtomicReference<Object>) setField.get(null);
                if (setRef != null) {
                    setRef.set(settingsRegistry);
                }
            }

            if (noiseRegistry != null) {
                Field noiseField = lifecyclePlatformClass.getDeclaredField("NOISE");
                noiseField.setAccessible(true);
                //noinspection unchecked
                AtomicReference<Object> noiseRef = (AtomicReference<Object>) noiseField.get(null);
                if (noiseRef != null) {
                    noiseRef.set(noiseRegistry);
                }
            }

            Class<?> lifecycleUtilClass = Class.forName("com.dfsek.terra.lifecycle.util.LifecycleUtil");
            Method initMethod = null;
            for (Method m : lifecycleUtilClass.getMethods()) {
                if (m.getName().equals("initialize") && m.getParameterCount() == 2) {
                    initMethod = m;
                    break;
                }
            }

            if (initMethod != null) {
                initMethod.invoke(null, biomeRegistry, worldPresetRegistry);
                regnumworld$LOGGER.info("Registered Terra biomes and world preset 'terra:overworld/overworld' into dynamic registry.");
            }

            Class<?> hackClass = Class.forName("com.dfsek.terra.lifecycle.util.RegistryHack");
            Method terraBindMethod = hackClass.getMethod("terra_bind");
            if (hackClass.isInstance(biomeRegistry)) {
                terraBindMethod.invoke(biomeRegistry);
            }
            if (hackClass.isInstance(worldPresetRegistry)) {
                terraBindMethod.invoke(worldPresetRegistry);
            }
        } catch (ClassNotFoundException ignored) {
            // Terra mod is not installed
        } catch (Throwable t) {
            regnumworld$LOGGER.warn("Failed to inject Terra world preset into dynamic registry: {}", t.getMessage(), t);
        }
    }
}
