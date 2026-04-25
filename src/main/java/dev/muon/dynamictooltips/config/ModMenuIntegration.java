package dev.muon.dynamictooltips.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.muon.dynamictooltips.DynamicTooltips;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Delegate to FzzyConfig — it owns the ConfigScreen UI. FzzyConfig also registers its own ConfigModMenuCompat
        // This is mostly vestigial (could most likely be left out without issue) but also a fallback
        return parent -> {
            ConfigApi.INSTANCE.openScreen(DynamicTooltips.MODID);
            return parent;
        };
    }
}
