package dev.muon.dynamictooltips;

import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class DynamicTooltips implements ModInitializer {

    public static final String MODID = "dynamictooltips";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void onInitialize() {
        DynamicTooltipsConfig.register();
        Keybindings.register();
    }
}
