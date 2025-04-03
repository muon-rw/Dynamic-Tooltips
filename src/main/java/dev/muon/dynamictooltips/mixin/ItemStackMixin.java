package dev.muon.dynamictooltips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.dynamictooltips.AttributeTooltipHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = ItemStack.class, remap = true)
public class ItemStackMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("DynamicTooltips-Mixin");
    
    @ModifyReturnValue(
            method = "getTooltip",
            at = @At("RETURN")
    )
    private List<Text> applyEnhancedAttributeTooltips(List<Text> tooltip, Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type) {
        if (!(player instanceof ClientPlayerEntity)) return tooltip;
        ItemStack stack = (ItemStack)(Object)this;

        LOGGER.info("Processing tooltip for: {}", stack.getName().getString());
        LOGGER.info("Tooltip type: {}", type);
        LOGGER.info("Original tooltip size: {}", tooltip.size());

        // Log original tooltip contents
        for (int i = 0; i < tooltip.size(); i++) {
            LOGGER.info("Original Line {}: \"{}\"", i, tooltip.get(i).getString());
        }

        // Process tooltips
        AttributeTooltipHandler.processTooltip(stack, tooltip, player);

        LOGGER.info("Processed tooltip size: {}", tooltip.size());

        // Log processed tooltip
        for (int i = 0; i < tooltip.size(); i++) {
            LOGGER.info("Processed Line {}: \"{}\"", i, tooltip.get(i).getString());
        }

        return tooltip;
    }
}