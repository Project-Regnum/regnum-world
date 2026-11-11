package zooller.regnumworld;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zooller.regnumworld.command.RegnumCommand;
import zooller.regnumworld.config.RegnumConfig;

public class RegnumWorld implements ModInitializer {
    public static final String MOD_ID = "regnum-world";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Regnum World mod...");

        // Load or create configuration
        RegnumConfig.load();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            RegnumCommand.register(dispatcher);
        });

        LOGGER.info("Regnum World initialized successfully. Regional resources and crop growth enabled.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
