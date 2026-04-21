package dev.muon.dynamictooltips.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.muon.dynamictooltips.DynamicTooltips;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Delegate to FzzyConfig — it owns the ConfigScreen UI. FzzyConfig also registers its own
        // ConfigModMenuCompat; this fallback handles the case where ModMenu picks our entrypoint first.
        return parent -> {
            ConfigApi.INSTANCE.openScreen(DynamicTooltips.MODID);
            return parent;
        };
    }
}
