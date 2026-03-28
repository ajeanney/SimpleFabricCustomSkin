package fr.jeanney;

import fr.jeanney.command.SkinCommand;
import fr.jeanney.skin.SkinManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleFabricCustomSkin implements ModInitializer {

    public static final String MOD_ID = "simplefabriccustomskin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SkinManager.initialize();
        CommandRegistrationCallback.EVENT.register(SkinCommand::register);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SkinManager.cacheOriginalSkin(handler.getPlayer());
            SkinManager.applySavedSkin(handler.getPlayer(), true);
        });
    }
}
