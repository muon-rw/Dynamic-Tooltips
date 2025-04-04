package dev.muon.dynamictooltips.mixin;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import dev.muon.dynamictooltips.DynamicTooltips;

import java.util.List;

public class DynamicTooltipsMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.startsWith("net.darkhax.enchdesc")) {
            DynamicTooltips.LOGGER.info("Disabled Enchantment Descriptions Mixin: " + mixinClassName);
            return true;
        }
        return false;
    }
}