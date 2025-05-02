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
import dev.muon.dynamictooltips.config.DynamicTooltipsConfig;
import dev.muon.dynamictooltips.DynamicTooltips;
import java.util.Optional;


@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {

    @ModifyReturnValue(
            method = "getFullname(Lnet/minecraft/core/Holder;I)Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN")
    )
    private static Component dynamictooltips$colorEnchantmentName(Component original, @Local(argsOnly = true) Holder<Enchantment> enchantment, @Local(argsOnly = true) int level) {

        if (!DynamicTooltipsConfig.CLIENT.colorEnchantmentNames.get()) {
            return original;
        }

        // Curses use vanilla red, never color them here
        if (enchantment.is(EnchantmentTags.CURSE)) {
            return original;
        }

        MutableComponent mutableOriginal = (original instanceof MutableComponent) ? (MutableComponent) original : null;
        if (mutableOriginal == null) {
            return original; // Should not happen, but safe check
        }

        Optional<String> hexColorToApply = Optional.empty();
        boolean isSuperLeveled = level > enchantment.value().getMaxLevel();

        if (isSuperLeveled) {
            // Apply super-level color if it's super-leveled
            hexColorToApply = Optional.of(DynamicTooltipsConfig.CLIENT.superLeveledEnchantmentColor.get());
        } else {
            // For regular enchantments, only apply color if user changed it from the default gray
            String configuredRegularColor = DynamicTooltipsConfig.CLIENT.enchantmentNameColor.get();
            if (!"#AAAAAA".equalsIgnoreCase(configuredRegularColor)) {
                hexColorToApply = Optional.of(configuredRegularColor);
            }
        }

        if (hexColorToApply.isPresent()) {
            String hex = hexColorToApply.get();
            try {
                int color = dynamictooltips$parseHexColor(hex);
                return mutableOriginal.withStyle(style -> style.withColor(color));
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                DynamicTooltips.LOGGER.warn("Invalid hex color format in config: '{}'. Using original color.", hex, e);
            }
        }

        return original;
    }

    @Unique
    private static int dynamictooltips$parseHexColor(String hexColor) throws NumberFormatException, StringIndexOutOfBoundsException {
        return Integer.parseInt(hexColor.substring(1), 16);
    }
} 