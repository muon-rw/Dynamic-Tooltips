package dev.muon.dynamictooltips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.dynamictooltips.AttributeTooltipHandler;
import dev.muon.dynamictooltips.EnchantmentTooltipHandler;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import java.util.Optional;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At("HEAD")
    )
    private void dynamictooltips$resetPromptFlag(Item.TooltipContext context, Player player, TooltipFlag flags, CallbackInfoReturnable<List<Component>> cir) {
        EnchantmentTooltipHandler.promptAddedThisTick = false;
    }

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

        if (!Screen.hasShiftDown() && !EnchantmentTooltipHandler.promptAddedThisTick) {
            boolean hasMergedModifiers = false;
            Integer mergedBaseColor = AttributeTooltipHandler.MERGED_BASE_COLOR.getColor();
            Integer mergedModifierColor = AttributeTooltipHandler.MERGED_MODIFIER_COLOR;
            
            for (Component line : tooltip) {
                boolean foundColorInLine = line.visit((style, text) -> {
                    Integer color = style.getColor() != null ? style.getColor().getValue() : null;
                    if (color != null && (color.equals(mergedBaseColor) || color.equals(mergedModifierColor))) {
                        return Optional.of(true);
                    }
                    return Optional.empty();
                }, Style.EMPTY).orElse(false);

                if (foundColorInLine) {
                    hasMergedModifiers = true;
                    break;
                }
            }
            
            if (hasMergedModifiers) {
                tooltip.add(EnchantmentTooltipHandler.SHIFT_PROMPT);
            }
        }

        return tooltip;
    }

    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V", ordinal = 2)
    )
    private void dynamictooltips$beforeEnchantmentTooltips(Item.TooltipContext context, Player player, TooltipFlag flags, CallbackInfoReturnable<List<Component>> cir) {
        EnchantmentTooltipHandler.getInstance().setupContext((ItemStack) (Object) this);
    }

    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V", ordinal = 3, shift = At.Shift.AFTER)
    )
    private void dynamictooltips$afterEnchantmentTooltips(Item.TooltipContext context, Player player, TooltipFlag flags, CallbackInfoReturnable<List<Component>> cir, @Local List<Component> list) {
        ItemStack stack = (ItemStack) (Object) this;
        EnchantmentTooltipHandler.getInstance().revertContext(stack);

        if (!Screen.hasShiftDown() && EnchantmentTooltipHandler.itemHasExpandableEnchantments(stack)) {
            if (!EnchantmentTooltipHandler.promptAddedThisTick) {
                list.add(EnchantmentTooltipHandler.SHIFT_PROMPT);
                EnchantmentTooltipHandler.promptAddedThisTick = true;
            }
        }
    }
}