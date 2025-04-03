package dev.muon.dynamictooltips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.muon.dynamictooltips.AttributeTooltipHandler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    
    @ModifyReturnValue(
            method = "getTooltipLines",
            at = @At("RETURN")
    )
    private List<Component> modifyTooltipLines(List<Component> tooltip, Item.TooltipContext context, @Nullable Player player, TooltipFlag type) {
        if (!(player instanceof LocalPlayer) || context == null || Minecraft.getInstance() == null || Minecraft.getInstance().level == null) {
             return tooltip;
        }
        ItemStack stack = (ItemStack)(Object)this;

        AttributeTooltipHandler.processTooltip(stack, tooltip, player);

        return tooltip;
    }
}