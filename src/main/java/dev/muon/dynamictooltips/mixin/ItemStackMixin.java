package dev.muon.dynamictooltips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.dynamictooltips.Keybindings;
import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;
import dev.muon.dynamictooltips.handlers.AttributeTooltipHandler;
import dev.muon.dynamictooltips.handlers.EnchantmentTooltipHandler;
import dev.muon.dynamictooltips.handlers.TooltipPromptHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ListIterator;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    // Modify the final tooltip list after all vanilla processing
    @ModifyReturnValue(
            method = "getTooltipLines",
            at = @At("RETURN")
    )
    private List<Component> modifyTooltipLines(List<Component> tooltip, Item.TooltipContext context, @Nullable Player player, TooltipFlag type) {
        if (!(player instanceof LocalPlayer) || context == null || Minecraft.getInstance() == null || Minecraft.getInstance().level == null) {
             return tooltip;
        }

        ItemStack stack = (ItemStack)(Object)this;

        // Process attributes first, potentially modifying the tooltip and getting the result
        AttributeTooltipHandler.ProcessingResult result = AttributeTooltipHandler.processTooltip(stack, tooltip, player);

        // Clean up any original attribute headers that might remain if attributes were merged
        if (result.modified() && result.finalHeader() != null) {
            Component correctHeader = result.finalHeader();
            ListIterator<Component> iterator = tooltip.listIterator();
            while (iterator.hasNext()) {
                Component currentLine = iterator.next();
                EquipmentSlotGroup slotGroup = AttributeTooltipHandler.getSlotFromText(currentLine);
                if (slotGroup != null && !currentLine.equals(correctHeader)) {
                    iterator.remove();
                }
            }
        }

        // Single source of truth for the expand prompt:
        //   - If attributes need it (merged or not), place at the end (below attributes).
        //   - Else if only enchants need it, splice in right below the enchant section
        //     so the hint sits between enchants and attributes (or at the end if the item
        //     has no attribute section at all).
        if (DynamicTooltipsConfig.INSTANCE.showUsabilityHint.get() && !Keybindings.isDetailedView()) {
            if (result.needsShiftPrompt()) {
                tooltip.add(TooltipPromptHandler.getExpandPrompt());
            } else if (EnchantmentTooltipHandler.itemHasExpandableEnchantments(stack)) {
                int insertIdx = TooltipPromptHandler.findEnchantSectionEnd(tooltip);
                if (insertIdx < 0) {
                    tooltip.add(TooltipPromptHandler.getExpandPrompt());
                } else {
                    tooltip.add(insertIdx, TooltipPromptHandler.getExpandPrompt());
                }
            }
        }

        return tooltip;
    }

    // Inject before vanilla enchantment tooltips are added to set up context
    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;addDetailsToTooltip(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;)V")
    )
    private void dynamictooltips$beforeEnchantmentTooltips(Item.TooltipContext context, Player player, TooltipFlag flags, CallbackInfoReturnable<List<Component>> cir) {
        EnchantmentTooltipHandler.getInstance().setupContext((ItemStack) (Object) this);
    }

    // Revert the enchantment-context hack once vanilla is done rendering enchant lines.
    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;addDetailsToTooltip(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;)V",
            shift = At.Shift.AFTER)
    )
    private void dynamictooltips$afterEnchantmentTooltips(Item.TooltipContext context, Player player, TooltipFlag flags, CallbackInfoReturnable<List<Component>> cir, @Local List<Component> list) {
        EnchantmentTooltipHandler.getInstance().revertContext((ItemStack) (Object) this);
    }
}
