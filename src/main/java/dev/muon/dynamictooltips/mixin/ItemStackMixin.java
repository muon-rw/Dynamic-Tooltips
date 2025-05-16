package dev.muon.dynamictooltips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.dynamictooltips.AttributeTooltipHandler;
import dev.muon.dynamictooltips.EnchantmentTooltipHandler;
import dev.muon.dynamictooltips.TooltipPromptHandler;
import net.minecraft.world.entity.EquipmentSlotGroup;
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
import java.util.ListIterator;
import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;
import dev.muon.dynamictooltips.Keybindings;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    // Reset the shift prompt flag at the beginning of tooltip generation for each item
    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At("HEAD")
    )
    private void dynamictooltips$resetPromptFlag(Item.TooltipContext context, Player player, TooltipFlag flags, CallbackInfoReturnable<List<Component>> cir) {
        TooltipPromptHandler.promptAddedThisTick = false;
    }

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

        if (DynamicTooltipsConfig.CLIENT.showUsabilityHint.get() && !Keybindings.isDetailedView() && !TooltipPromptHandler.promptAddedThisTick) {
            if (result.needsShiftPrompt()) {
                tooltip.add(TooltipPromptHandler.getExpandPrompt());
                TooltipPromptHandler.promptAddedThisTick = true;
            }
        }

        return tooltip;
    }

    // Inject before vanilla enchantment tooltips are added to set up context
    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V", ordinal = 2)
    )
    private void dynamictooltips$beforeEnchantmentTooltips(Item.TooltipContext context, Player player, TooltipFlag flags, CallbackInfoReturnable<List<Component>> cir) {
        EnchantmentTooltipHandler.getInstance().setupContext((ItemStack) (Object) this);
    }

    // Inject after vanilla enchantment tooltips to revert context and potentially add shift prompt
    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V", ordinal = 3, shift = At.Shift.AFTER)
    )
    private void dynamictooltips$afterEnchantmentTooltips(Item.TooltipContext context, Player player, TooltipFlag flags, CallbackInfoReturnable<List<Component>> cir, @Local List<Component> list) {
        ItemStack stack = (ItemStack) (Object) this;
        EnchantmentTooltipHandler.getInstance().revertContext(stack);

        // Use Keybindings.isDetailedView()
        if (DynamicTooltipsConfig.CLIENT.showUsabilityHint.get() && !Keybindings.isDetailedView() && EnchantmentTooltipHandler.itemHasExpandableEnchantments(stack)) {
            if (!TooltipPromptHandler.promptAddedThisTick) {
                list.add(TooltipPromptHandler.getExpandPrompt());
                TooltipPromptHandler.promptAddedThisTick = true;
            }
        }
    }
}