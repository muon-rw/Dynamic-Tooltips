package dev.muon.dynamictooltips.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.muon.dynamictooltips.EnchantmentContext;
import dev.muon.dynamictooltips.EnchantmentTooltipHandler;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

@Mixin(ItemEnchantments.class)
public class ItemEnchantmentsMixin implements EnchantmentContext {

    @Unique
    private ItemStack dynamictooltips$heldStack = ItemStack.EMPTY;

    @Override
    public ItemStack dynamictooltips$getStack() {
        return this.dynamictooltips$heldStack;
    }

    @Override
    public void dynamictooltips$setStack(ItemStack stack) {
        this.dynamictooltips$heldStack = stack;
    }

    @Inject(
            method = "addToTooltip(Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void dynamictooltips$addDescriptionSorted(Item.TooltipContext context, Consumer<Component> tooltipConsumer, TooltipFlag flag, CallbackInfo ci,
                                                      @Local Holder<Enchantment> enchantment, @Local int level) {
        if (!this.dynamictooltips$heldStack.isEmpty()) {
            EnchantmentTooltipHandler.getInstance().insertDescriptions(enchantment, level, tooltipConsumer);
        }
    }

    @Inject(
            method = "addToTooltip(Lnet/minecraft/world/item/Item$TooltipContext;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 1, shift = At.Shift.AFTER)
    )
    private void dynamictooltips$addDescriptionUnsorted(Item.TooltipContext context, Consumer<Component> tooltipConsumer, TooltipFlag flag, CallbackInfo ci,
                                                        @Local Object2IntMap.Entry<Holder<Enchantment>> entry) {
        if (!this.dynamictooltips$heldStack.isEmpty()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();
            EnchantmentTooltipHandler.getInstance().insertDescriptions(enchantment, level, tooltipConsumer);
        }
    }
}