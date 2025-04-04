package dev.muon.dynamictooltips.mixin.compat.bettercombat;

import net.bettercombat.client.WeaponAttributeTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WeaponAttributeTooltip.class)
public class WeaponAttributeTooltipMixin {
    /**
     * Cancelling the original logic, to be handled instead in AttackRangeTooltipHandler
     */
    @Inject(method = "modifyTooltip", at = @At("HEAD"), cancellable = true)
    private static void cancelTooltip(ItemStack itemStack, List<Component> lines, CallbackInfo ci) {
        ci.cancel();
    }
}