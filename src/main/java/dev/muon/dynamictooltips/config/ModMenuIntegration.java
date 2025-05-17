package dev.muon.dynamictooltips.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import dev.muon.dynamictooltips.DynamicTooltips;

import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigurationScreen(DynamicTooltips.MODID, parent);
    }
} 