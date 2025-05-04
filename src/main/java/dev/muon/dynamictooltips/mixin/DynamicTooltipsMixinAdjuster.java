package dev.muon.dynamictooltips.mixin;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import dev.muon.dynamictooltips.DynamicTooltips;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.Inject;

import java.util.Arrays;
import java.util.List;

public class DynamicTooltipsMixinAdjuster implements MixinAnnotationAdjuster {

    private static final List<String> TIERED_METHODS_TO_DISABLE = Arrays.asList(
            "appendAttributeModifiersTooltipTwoMixin",
            "appendAttributeModifierTooltipMixin",
            "appendAttributeModifierTooltipTwoMixin",
            "appendAttributeModifierTooltipThreeMixin"
    );

    @Override
    public AdjustableAnnotationNode adjust(List<String> targetClassNames, String mixinClassName, MethodNode method, AdjustableAnnotationNode annotation) {
        if (!mixinClassName.equals("draylar.tiered.mixin.client.ItemStackClientMixin")) {
            return annotation;
        }

        if (annotation.is(Inject.class)) {
            String methodName = method.name;
            if (TIERED_METHODS_TO_DISABLE.contains(methodName)) {
                 DynamicTooltips.LOGGER.info("Adjuster: Disabling @Inject in TieredZ mixin {}.{}", mixinClassName, methodName);
                 return null;
            }
        }

        return annotation;
    }
} 