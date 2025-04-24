package dev.muon.dynamictooltips.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.sugar.Local;


@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {

    @ModifyReturnValue(
            method = "getFullname(Lnet/minecraft/core/Holder;I)Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN")
    )
    private static Component dynamictooltips$colorEnchantmentName(Component original, @Local(argsOnly = true) Holder<Enchantment> enchantment, @Local(argsOnly = true) int level) {

        if (level > enchantment.value().getMaxLevel() && !enchantment.is(EnchantmentTags.CURSE)) {
            if (original instanceof MutableComponent mutableOriginal) {
                return mutableOriginal.withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE));
            }
        }
        return original;
    }
} 