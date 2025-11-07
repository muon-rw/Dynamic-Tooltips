package dev.muon.dynamictooltips;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.neoforged.fml.config.ModConfig;

@Environment(EnvType.CLIENT)
public class DynamicTooltips implements ModInitializer {

    public static final String MODID = "dynamictooltips";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
    @Override
    public void onInitialize() {
        ConfigRegistry.INSTANCE.register(MODID, ModConfig.Type.CLIENT, DynamicTooltipsConfig.CLIENT_SPEC);
        Keybindings.register();
    }
}