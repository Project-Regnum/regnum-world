package zooller.regnumworld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import zooller.regnumworld.config.BiomeGrowthConfig;
import zooller.regnumworld.config.BiomeMobConfig;
import zooller.regnumworld.config.BiomeWorldgenConfig;
import zooller.regnumworld.config.RegnumConfig;

import java.util.Map;

public class RegnumCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("regnum")
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(RegnumCommand::executeReload))
                        .then(Commands.literal("info")
                                .executes(RegnumCommand::executeInfo))
        );

        // Alias /rw
        dispatcher.register(
                Commands.literal("rw")
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(RegnumCommand::executeReload))
                        .then(Commands.literal("info")
                                .executes(RegnumCommand::executeInfo))
        );
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        RegnumConfig.load();
        ctx.getSource().sendSuccess(
                () -> Component.translatable("command.regnum.prefix")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.translatable("command.regnum.reload.success")
                                .withStyle(ChatFormatting.GREEN)),
                true
        );
        return 1;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Entity entity = source.getEntity();

        if (entity == null) {
            source.sendFailure(Component.translatable("command.regnum.error.player_only"));
            return 0;
        }

        BlockPos pos = entity.blockPosition();
        Holder<Biome> biomeHolder = entity.level().getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);

        if (biomeId == null) {
            source.sendFailure(Component.translatable("command.regnum.error.no_biome"));
            return 0;
        }

        RegnumConfig config = RegnumConfig.getInstance();
        BiomeGrowthConfig growthConfig = config.biomeGrowth.get(biomeId.toString());
        BiomeWorldgenConfig worldgenConfig = config.biomeWorldgen.get(biomeId.toString());
        BiomeMobConfig mobConfig = config.biomeMobs.get(biomeId.toString());

        source.sendSuccess(() -> Component.translatable("command.regnum.info.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // Biome name (uses vanilla translatable name + identifier)
        String biomeTransKey = "biome." + biomeId.getNamespace() + "." + biomeId.getPath();
        source.sendSuccess(() -> Component.translatable("command.regnum.info.biome")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(biomeTransKey).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" (" + biomeId + ")").withStyle(ChatFormatting.DARK_GRAY)), false);

        // Check underground / mine status
        boolean isUnderground = pos.getY() <= config.undergroundMaxY || (config.blockCaveCropGrowthWithoutSky && !entity.level().canSeeSky(pos));
        if (config.blockUndergroundCropGrowth && isUnderground) {
            source.sendSuccess(() -> Component.translatable("command.regnum.info.underground.label")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.translatable("command.regnum.info.underground.blocked")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)), false);
        }

        // Growth info
        double defaultGrowth = growthConfig != null ? growthConfig.defaultMultiplier : 1.0;
        if (config.mountainAltitudeDebuffEnabled && pos.getY() >= config.mountainAltitudeStartY) {
            defaultGrowth *= config.mountainAltitudeMultiplier;
        }
        final double finalGrowth = defaultGrowth;
        source.sendSuccess(() -> Component.translatable("command.regnum.info.crop_growth")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.format("%.1fx", finalGrowth)).withStyle(getMultiplierColor(finalGrowth))), false);

        if (growthConfig != null && !growthConfig.crops.isEmpty()) {
            MutableComponent cropsLine = Component.empty();
            int count = 0;
            for (Map.Entry<String, Double> entry : growthConfig.crops.entrySet()) {
                if (count > 0) {
                    cropsLine.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                String cropKey = entry.getKey();
                String blockTransKey = cropKey.contains(":") ? "block." + cropKey.replace(":", ".") : "block.minecraft." + cropKey;
                cropsLine.append(Component.translatable(blockTransKey).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format("%.1fx", entry.getValue())).withStyle(getMultiplierColor(entry.getValue())));
                count++;
            }
            final MutableComponent finalCropsLine = cropsLine;
            source.sendSuccess(() -> Component.translatable("command.regnum.info.special_crops")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(finalCropsLine), false);
        }

        // Worldgen info
        double treeMult = worldgenConfig != null ? worldgenConfig.treeMultiplier : 1.0;
        double flowerMult = worldgenConfig != null ? worldgenConfig.flowerMultiplier : 1.0;
        double grassMult = worldgenConfig != null ? worldgenConfig.grassMultiplier : 1.0;

        source.sendSuccess(() -> Component.translatable("command.regnum.info.vegetation")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("command.regnum.info.vegetation.trees", String.format("%.1fx", treeMult)).withStyle(getMultiplierColor(treeMult)))
                .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("command.regnum.info.vegetation.flowers", String.format("%.1fx", flowerMult)).withStyle(getMultiplierColor(flowerMult)))
                .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("command.regnum.info.vegetation.grass", String.format("%.1fx", grassMult)).withStyle(getMultiplierColor(grassMult))), false);

        if (worldgenConfig != null && !worldgenConfig.oreMultipliers.isEmpty()) {
            MutableComponent oresLine = Component.empty();
            int count = 0;
            for (Map.Entry<String, Double> entry : worldgenConfig.oreMultipliers.entrySet()) {
                if (count > 0) {
                    oresLine.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                String oreKey = entry.getKey();
                oresLine.append(Component.translatable("ore.regnum." + oreKey.toLowerCase()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format("%.1fx", entry.getValue())).withStyle(getMultiplierColor(entry.getValue())));
                count++;
            }
            final MutableComponent finalOresLine = oresLine;
            source.sendSuccess(() -> Component.translatable("command.regnum.info.ore_frequency")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(finalOresLine), false);
        }

        // Mob spawn info
        double hostileMult = mobConfig != null ? mobConfig.hostileMultiplier : 1.0;
        double creatureMult = mobConfig != null ? mobConfig.creatureMultiplier : 1.0;
        source.sendSuccess(() -> Component.translatable("command.regnum.info.mob_spawns")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("command.regnum.info.mob_spawns.monsters", String.format("%.1fx", hostileMult)).withStyle(getMultiplierColor(hostileMult)))
                .append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("command.regnum.info.mob_spawns.animals", String.format("%.1fx", creatureMult)).withStyle(getMultiplierColor(creatureMult))), false);

        // Animal growth info
        double animalGrowthMult = config.getAnimalGrowthMultiplier(biomeId);
        source.sendSuccess(() -> Component.translatable("command.regnum.info.animal_growth")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.format("%.1fx", animalGrowthMult)).withStyle(getMultiplierColor(animalGrowthMult))), false);

        // Fishing info
        double fishingMult = config.getFishingMultiplier(biomeId);
        source.sendSuccess(() -> Component.translatable("command.regnum.info.fishing")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.format("%.1fx", fishingMult)).withStyle(getMultiplierColor(fishingMult))), false);

        source.sendSuccess(() -> Component.translatable("command.regnum.info.footer")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static ChatFormatting getMultiplierColor(double multiplier) {
        if (multiplier > 1.0) return ChatFormatting.GREEN;
        if (multiplier < 1.0) return ChatFormatting.RED;
        return ChatFormatting.WHITE;
    }
}
