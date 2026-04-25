package dev.muon.dynamictooltips.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import dev.muon.dynamictooltips.api.DynamicTooltipsAPI;
import dev.muon.dynamictooltips.handlers.AttributeTooltipHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.text.DecimalFormat;

/**
 * Reroutes the per-modifier value formatting in {@link PotionContents#addPotionTooltip} through
 * {@link AttributeTooltipHandler}'s percent map so attribute mods that only register their
 * percent rules via {@link DynamicTooltipsAPI} get correct formatting in potion tooltips too.
 */
@Mixin(PotionContents.class)
public class PotionContentsMixin {

    @WrapOperation(
            method = "addPotionTooltip",
            at = @At(value = "INVOKE", target = "Ljava/text/DecimalFormat;format(D)Ljava/lang/String;")
    )
    private static String dynamictooltips$applyPercentRule(
            DecimalFormat format, double value, Operation<String> original,
            @Local AttributeModifier modifier,
            @Local Pair<Holder<Attribute>, AttributeModifier> entry) {

        if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
            Identifier attrId = BuiltInRegistries.ATTRIBUTE.getKey(entry.getFirst().value());
            if (attrId != null) {
                DynamicTooltipsAPI.PercentRule rule = AttributeTooltipHandler.getPercentRule(attrId);
                if (rule != null) {
                    if (rule.displayFunction() != null) {
                        return rule.displayFunction().format(value);
                    }
                    return original.call(format, value * rule.scaleFactor()) + "%";
                }
            }
        }
        return original.call(format, value);
    }
}
